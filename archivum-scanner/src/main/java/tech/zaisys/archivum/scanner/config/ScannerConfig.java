package tech.zaisys.archivum.scanner.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for the scanner application.
 * Loaded from YAML file, environment variables, and CLI options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerConfig {

    /**
     * Server configuration (not used in MVP dry-run mode)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerConfig {
        private String url;
        private String apiKey;
        private Integer timeout; // seconds
        private Integer retries;
    }

    /**
     * Scanner-specific settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScannerSettings {
        private Integer threads; // 0 = auto-detect
        private Integer batchSize;
        private Boolean skipSystemDirs;
        private Boolean followSymlinks;
        private List<String> excludePatterns;
        private Long archivePromptThreshold; // bytes
        private List<String> autoPostponePatterns;
    }

    /**
     * Copy settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CopySettings {
        private Boolean enabled;
        private Integer threads;
        private Boolean verify;
        private Boolean skipExisting;
        private Boolean flatten;
    }

    /**
     * Dry-run mode settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DryRunSettings {
        private Boolean enabled;
        private String outputDir;
    }

    /**
     * Metadata extraction settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataSettings {
        private Boolean extractExif;
        private Boolean detectMimeType;
        private Boolean exifOptimization; // Skip EXIF for duplicate hashes
    }

    /**
     * Logging settings
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoggingSettings {
        private String level; // DEBUG, INFO, WARN, ERROR
        private String file;
        private Boolean console;
    }

    // Main configuration fields
    private ServerConfig server;
    private ScannerSettings scanner;
    private CopySettings copy;
    private DryRunSettings dryRun;
    private MetadataSettings metadata;
    private LoggingSettings logging;

    /**
     * Get the number of hash worker threads.
     * Returns CPU count if set to 0 or null.
     */
    public int getHashThreads() {
        if (scanner == null || scanner.threads == null || scanner.threads == 0) {
            return Runtime.getRuntime().availableProcessors();
        }
        return scanner.threads;
    }

    /**
     * Get the batch size for file processing.
     * Defaults to 1000 if not set.
     */
    public int getBatchSize() {
        if (scanner == null || scanner.batchSize == null) {
            return 1000;
        }
        return scanner.batchSize;
    }

    /**
     * Get the output directory for dry-run mode.
     * Defaults to ~/.archivum/scans if not set.
     */
    public Path getOutputDir() {
        String dir = dryRun != null && dryRun.outputDir != null
            ? dryRun.outputDir
            : System.getProperty("user.home") + "/.archivum/scans";

        // Expand ~ to home directory
        if (dir.startsWith("~")) {
            dir = System.getProperty("user.home") + dir.substring(1);
        }

        return Path.of(dir);
    }

    /**
     * Check if dry-run mode is enabled.
     * Defaults to true for MVP.
     */
    public boolean isDryRun() {
        return dryRun == null || dryRun.enabled == null || dryRun.enabled;
    }

    /**
     * Check if system directories should be skipped.
     * Defaults to true.
     */
    public boolean shouldSkipSystemDirs() {
        return scanner == null || scanner.skipSystemDirs == null || scanner.skipSystemDirs;
    }

    /**
     * Check if symbolic links should be followed.
     * Defaults to false.
     */
    public boolean shouldFollowSymlinks() {
        return scanner != null && scanner.followSymlinks != null && scanner.followSymlinks;
    }

    /**
     * Get exclude patterns.
     * Returns empty list if not set.
     */
    public List<String> getExcludePatterns() {
        if (scanner == null || scanner.excludePatterns == null) {
            return List.of();
        }
        return scanner.excludePatterns;
    }

    /**
     * Check if EXIF extraction is enabled.
     * Defaults to true.
     */
    public boolean isExifExtractionEnabled() {
        return metadata == null || metadata.extractExif == null || metadata.extractExif;
    }

    /**
     * Check if EXIF optimization is enabled (skip for duplicate hashes).
     * Defaults to true.
     */
    public boolean isExifOptimizationEnabled() {
        return metadata == null || metadata.exifOptimization == null || metadata.exifOptimization;
    }
}
