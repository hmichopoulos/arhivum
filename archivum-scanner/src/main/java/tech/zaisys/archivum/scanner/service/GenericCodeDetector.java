package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generic code detector for projects that don't match specific build tools.
 * Looks for common code patterns like src/ directories, source files, and .gitignore.
 */
@Slf4j
public class GenericCodeDetector implements ProjectDetector {

    private static final Set<String> CODE_EXTENSIONS = Set.of(
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
        "sh", "bash"                    // Shell scripts
    );

    private static final Set<String> CODE_INDICATORS = Set.of(
        "src",
        ".gitignore",
        "README.md",
        "LICENSE"
    );

    @Override
    public boolean canDetect(Path folder) {
        try {
            // Check for src/ directory
            if (Files.isDirectory(folder.resolve("src"))) {
                return true;
            }

            // Check for .gitignore (common in code projects)
            if (Files.exists(folder.resolve(".gitignore"))) {
                return true;
            }

            // Check if folder contains multiple source files
            long sourceFileCount = countSourceFiles(folder);
            return sourceFileCount >= 3; // At least 3 source files

        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        if (!canDetect(folder)) {
            return Optional.empty();
        }

        String name = folder.getFileName().toString();
        String identifier = "unknown:" + name;

        ProjectIdentityDto identity = ProjectIdentityDto.builder()
            .type(ProjectType.GENERIC)
            .name(name)
            .identifier(identifier)
            .build();

        log.debug("Detected generic code project: {}", identifier);
        return Optional.of(identity);
    }

    @Override
    public int getPriority() {
        return 0; // Lowest priority (fallback detector)
    }

    /**
     * Count source files in the folder (non-recursive, just top level and src/)
     */
    private long countSourceFiles(Path folder) throws IOException {
        long count = 0;

        // Check top level
        try (Stream<Path> files = Files.list(folder)) {
            count += files.filter(Files::isRegularFile)
                .filter(this::isSourceFile)
                .count();
        }

        // Check src/ directory
        Path srcDir = folder.resolve("src");
        if (Files.isDirectory(srcDir)) {
            try (Stream<Path> files = Files.walk(srcDir, 2)) { // Only 2 levels deep
                count += files.filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .count();
            }
        }

        return count;
    }

    /**
     * Check if a file is a source code file based on extension
     */
    private boolean isSourceFile(Path file) {
        String fileName = file.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return false;
        }

        String extension = fileName.substring(lastDot + 1).toLowerCase();
        return CODE_EXTENSIONS.contains(extension);
    }
}
