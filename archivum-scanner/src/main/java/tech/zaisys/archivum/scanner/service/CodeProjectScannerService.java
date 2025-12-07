package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Service for scanning a directory tree to detect code projects.
 * Identifies code project roots and extracts project metadata.
 */
@Slf4j
public class CodeProjectScannerService {

    private final CodeProjectDetectionService detectionService;
    private final Set<String> excludedFolders;

    public CodeProjectScannerService() {
        this.detectionService = new CodeProjectDetectionService();
        this.excludedFolders = Set.of(
            // Build artifacts
            "target", "build", "out", "dist", ".gradle",
            // Dependencies
            "node_modules", "vendor", ".venv", "venv", "__pycache__",
            // IDE
            ".idea", ".vscode", ".eclipse",
            // OS
            ".DS_Store", "Thumbs.db",
            // Version control
            ".git", ".svn", ".hg"
        );
    }

    /**
     * Scan result containing detected projects.
     */
    public record ScanResult(List<CodeProjectDto> projects) {}

    /**
     * Scan a directory tree for code projects.
     *
     * @param rootPath Root directory to scan
     * @param sourceId Source ID for the scan
     * @param fileHashes Map of file path to hash (from main scan)
     * @return ScanResult containing detected projects
     * @throws IOException if directory cannot be scanned
     */
    public ScanResult scanForProjects(Path rootPath, UUID sourceId, Map<Path, String> fileHashes) throws IOException {
        List<CodeProjectDto> projects = new ArrayList<>();
        Set<Path> processedPaths = new HashSet<>();

        // Walk the directory tree looking for project roots
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Skip if already processed (nested project)
                if (processedPaths.stream().anyMatch(dir::startsWith)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                // Skip excluded folders
                String dirName = dir.getFileName().toString();
                if (excludedFolders.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                // Try to detect project
                Optional<ProjectIdentityDto> identity = detectionService.detectProject(dir);
                if (identity.isPresent()) {
                    try {
                        CodeProjectDto project = buildProjectDto(
                            dir,
                            sourceId,
                            identity.get(),
                            fileHashes
                        );
                        projects.add(project);
                        processedPaths.add(dir);

                        log.info("Detected code project: {} at {}", identity.get().getIdentifier(), dir);

                        // Skip subtree (don't look for nested projects)
                        return FileVisitResult.SKIP_SUBTREE;

                    } catch (IOException e) {
                        log.warn("Failed to build project DTO for {}: {}", dir, e.getMessage());
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.debug("Cannot access: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Found {} code projects in {}", projects.size(), rootPath);
        return new ScanResult(projects);
    }

    /**
     * Build a CodeProjectDto from detected project.
     */
    private CodeProjectDto buildProjectDto(
        Path projectRoot,
        UUID sourceId,
        ProjectIdentityDto identity,
        Map<Path, String> fileHashes
    ) throws IOException {
        // Collect files within this project
        List<Path> projectFiles = collectProjectFiles(projectRoot);

        // Separate source files from all files
        List<Path> sourceFiles = projectFiles.stream()
            .filter(this::isSourceFile)
            .toList();

        // Calculate total size
        long totalSize = projectFiles.stream()
            .mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    return 0;
                }
            })
            .sum();

        // Compute content hash (hash of source file hashes)
        String contentHash = computeContentHash(sourceFiles, fileHashes);

        return CodeProjectDto.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .rootPath(projectRoot.toString())
            .identity(identity)
            .contentHash(contentHash)
            .sourceFileCount(sourceFiles.size())
            .totalFileCount(projectFiles.size())
            .totalSizeBytes(totalSize)
            .scannedAt(Instant.now())
            .build();
    }

    /**
     * Collect all files within a project (non-recursive beyond certain depth).
     */
    private List<Path> collectProjectFiles(Path projectRoot) throws IOException {
        List<Path> files = new ArrayList<>();

        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Skip excluded folders within project
                if (!dir.equals(projectRoot)) {
                    String dirName = dir.getFileName().toString();
                    if (excludedFolders.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Check if a file is a source file (not build artifact or dependency).
     */
    private boolean isSourceFile(Path file) {
        String fileName = file.getFileName().toString();
        String extension = getExtension(fileName);

        // Source file extensions
        Set<String> sourceExtensions = Set.of(
            "java", "kt", "kts",           // Java/Kotlin
            "js", "ts", "jsx", "tsx",      // JavaScript/TypeScript
            "py",                           // Python
            "go",                           // Go
            "rs",                           // Rust
            "c", "cpp", "cc", "h", "hpp",  // C/C++
            "cs",                           // C#
            "rb",                           // Ruby
            "php",                          // PHP
            "swift",                        // Swift
            "scala",                        // Scala
            "sh", "bash",                   // Shell scripts
            "xml", "gradle", "toml", "yaml", "yml", "json", "md" // Config files
        );

        return sourceExtensions.contains(extension);
    }

    /**
     * Compute content hash from source file hashes.
     * This creates a hash of all source file hashes sorted alphabetically.
     */
    private String computeContentHash(List<Path> sourceFiles, Map<Path, String> fileHashes) {
        try {
            // Get hashes of source files
            List<String> hashes = sourceFiles.stream()
                .map(fileHashes::get)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

            if (hashes.isEmpty()) {
                return "empty";
            }

            // Create hash of hashes
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String hash : hashes) {
                digest.update(hash.getBytes());
            }

            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            log.warn("Failed to compute content hash", e);
            return "error";
        }
    }

    /**
     * Get file extension.
     */
    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }
}
