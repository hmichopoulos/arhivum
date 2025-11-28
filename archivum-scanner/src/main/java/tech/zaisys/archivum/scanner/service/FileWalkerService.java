package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.scanner.config.ScannerConfig;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for walking directory trees and collecting files to scan.
 * Supports exclusion patterns and skipping system directories.
 */
@Slf4j
public class FileWalkerService {

    /**
     * Result of a directory walk containing files and total size.
     */
    public record WalkResult(List<Path> files, long totalSize) {}

    private static final Set<String> SYSTEM_DIRECTORIES = Set.of(
        ".Trash",
        ".Trashes",
        "$RECYCLE.BIN",
        "System Volume Information",
        ".TemporaryItems",
        ".Spotlight-V100",
        ".fseventsd"
    );

    private final ScannerConfig config;
    private final PathMatcher excludeMatcher;

    public FileWalkerService(ScannerConfig config) {
        this.config = config;
        this.excludeMatcher = createExcludeMatcher(config.getExcludePatterns());
    }

    /**
     * Walk a directory tree and collect all regular files.
     * Calculates total size during the walk for memory efficiency.
     *
     * @param rootPath Root directory to scan
     * @return WalkResult containing files and total size
     * @throws IOException if directory cannot be walked
     */
    public WalkResult walk(Path rootPath) throws IOException {
        List<Path> files = new ArrayList<>();
        long[] totalSize = {0L}; // Use array to allow modification in lambda

        FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldSkipDirectory(dir)) {
                    log.debug("Skipping directory: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !shouldExcludeFile(file)) {
                    files.add(file);
                    totalSize[0] += attrs.size();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Cannot access: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE; // Skip and continue
            }
        };

        Set<FileVisitOption> options = new HashSet<>();
        if (config.shouldFollowSymlinks()) {
            options.add(FileVisitOption.FOLLOW_LINKS);
        }

        Files.walkFileTree(rootPath, options, Integer.MAX_VALUE, visitor);

        log.info("Found {} files ({} bytes) in {}", files.size(), totalSize[0], rootPath);
        return new WalkResult(files, totalSize[0]);
    }

    /**
     * Check if a directory should be skipped.
     *
     * @param dir Directory path
     * @return true if should skip, false otherwise
     */
    private boolean shouldSkipDirectory(Path dir) {
        if (!config.shouldSkipSystemDirs()) {
            return false;
        }

        String dirName = dir.getFileName().toString();
        return SYSTEM_DIRECTORIES.contains(dirName);
    }

    /**
     * Check if a file should be excluded based on patterns.
     *
     * @param file File path
     * @return true if should exclude, false otherwise
     */
    private boolean shouldExcludeFile(Path file) {
        if (excludeMatcher == null) {
            return false;
        }

        // Match against filename only
        Path fileName = file.getFileName();
        return excludeMatcher.matches(fileName);
    }

    /**
     * Create a path matcher for exclude patterns.
     * Supports glob patterns like "*.tmp", ".DS_Store", etc.
     *
     * @param patterns List of glob patterns
     * @return PathMatcher or null if no patterns
     */
    private PathMatcher createExcludeMatcher(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }

        // Combine all patterns into a single glob pattern
        String combinedPattern = String.join(",", patterns);
        String globPattern = "glob:{" + combinedPattern + "}";

        return FileSystems.getDefault().getPathMatcher(globPattern);
    }

    /**
     * Count the number of files that would be scanned (without actually scanning).
     * Useful for progress estimation.
     *
     * @param rootPath Root directory
     * @return Number of files
     * @throws IOException if directory cannot be walked
     */
    public long countFiles(Path rootPath) throws IOException {
        return walk(rootPath).files().size();
    }
}
