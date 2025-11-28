package tech.zaisys.archivum.scanner.config;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loads scanner configuration from multiple sources with priority:
 * 1. CLI options (highest priority - handled by Picocli)
 * 2. Environment variables
 * 3. Configuration file
 * 4. Built-in defaults (lowest priority)
 */
@Slf4j
public class ConfigLoader {

    private static final String DEFAULT_CONFIG_PATH = System.getProperty("user.home") + "/.config/archivum/scanner.yml";
    private static final String ENV_CONFIG_PATH = "ARCHIVUM_CONFIG";
    private static final String ENV_API_KEY = "ARCHIVUM_API_KEY";
    private static final String ENV_OUTPUT_DIR = "ARCHIVUM_OUTPUT_DIR";

    /**
     * Load configuration from default location or environment variable.
     */
    public static ScannerConfig load() {
        String configPath = System.getenv(ENV_CONFIG_PATH);
        if (configPath != null) {
            return loadFromFile(Path.of(configPath));
        }

        Path defaultPath = Path.of(DEFAULT_CONFIG_PATH);
        if (Files.exists(defaultPath)) {
            return loadFromFile(defaultPath);
        }

        log.info("No configuration file found, using defaults");
        return getDefaults();
    }

    /**
     * Load configuration from a specific file.
     */
    public static ScannerConfig loadFromFile(Path configPath) {
        if (!Files.exists(configPath)) {
            log.warn("Configuration file not found: {}, using defaults", configPath);
            return getDefaults();
        }

        try (InputStream input = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml(new Constructor(ScannerConfig.class, new org.yaml.snakeyaml.LoaderOptions()));
            ScannerConfig config = yaml.load(input);

            // Apply environment variable overrides
            applyEnvironmentOverrides(config);

            log.info("Loaded configuration from: {}", configPath);
            return config;
        } catch (IOException e) {
            log.error("Failed to load configuration from: {}", configPath, e);
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Get default configuration.
     */
    public static ScannerConfig getDefaults() {
        ScannerConfig config = ScannerConfig.builder()
            .server(ScannerConfig.ServerConfig.builder()
                .url("https://archivum.local:8080")
                .apiKey(null)
                .timeout(30)
                .retries(3)
                .build())
            .scanner(ScannerConfig.ScannerSettings.builder()
                .threads(0) // auto-detect
                .batchSize(1000)
                .skipSystemDirs(true)
                .followSymlinks(false)
                .excludePatterns(List.of(
                    ".Trash",
                    ".Trashes",
                    "$RECYCLE.BIN",
                    "System Volume Information",
                    ".DS_Store",
                    "*.tmp",
                    "*.temp"
                ))
                .archivePromptThreshold(104857600L) // 100 MB
                .autoPostponePatterns(List.of("*.tib", "*.vhdx", "*.vmdk"))
                .build())
            .copy(ScannerConfig.CopySettings.builder()
                .enabled(false)
                .threads(4)
                .verify(true)
                .skipExisting(false)
                .flatten(false)
                .build())
            .dryRun(ScannerConfig.DryRunSettings.builder()
                .enabled(true) // MVP default
                .outputDir("~/.archivum/scans")
                .build())
            .metadata(ScannerConfig.MetadataSettings.builder()
                .extractExif(true)
                .detectMimeType(true)
                .exifOptimization(true)
                .build())
            .logging(ScannerConfig.LoggingSettings.builder()
                .level("INFO")
                .file("~/.archivum/scanner.log")
                .console(true)
                .build())
            .build();

        // Apply environment variable overrides
        applyEnvironmentOverrides(config);

        return config;
    }

    /**
     * Apply environment variable overrides to configuration.
     */
    private static void applyEnvironmentOverrides(ScannerConfig config) {
        // API key from environment
        String apiKey = System.getenv(ENV_API_KEY);
        if (apiKey != null && config.getServer() != null) {
            config.getServer().setApiKey(apiKey);
        }

        // Output directory from environment
        String outputDir = System.getenv(ENV_OUTPUT_DIR);
        if (outputDir != null && config.getDryRun() != null) {
            config.getDryRun().setOutputDir(outputDir);
        }
    }

    /**
     * Create a default configuration file at the standard location.
     */
    public static void createDefaultConfigFile() throws IOException {
        Path configPath = Path.of(DEFAULT_CONFIG_PATH);

        if (Files.exists(configPath)) {
            log.warn("Configuration file already exists: {}", configPath);
            return;
        }

        // Create parent directories
        Files.createDirectories(configPath.getParent());

        // Write default configuration
        String defaultConfig = """
            # Archivum Scanner Configuration

            # Server connection (not used in MVP dry-run mode)
            server:
              url: "https://archivum.local:8080"
              apiKey: "${ARCHIVUM_API_KEY}"
              timeout: 30
              retries: 3

            # Scanner settings
            scanner:
              threads: 0  # 0 = auto-detect CPU count
              batchSize: 1000
              skipSystemDirs: true
              followSymlinks: false
              excludePatterns:
                - ".Trash"
                - ".Trashes"
                - "$RECYCLE.BIN"
                - "System Volume Information"
                - ".DS_Store"
                - "*.tmp"
                - "*.temp"
              archivePromptThreshold: 104857600  # 100 MB
              autoPostponePatterns:
                - "*.tib"
                - "*.vhdx"
                - "*.vmdk"

            # Copy settings
            copy:
              enabled: false
              threads: 4
              verify: true
              skipExisting: false
              flatten: false

            # Dry-run mode (MVP default)
            dryRun:
              enabled: true
              outputDir: "~/.archivum/scans"

            # Metadata extraction
            metadata:
              extractExif: true
              detectMimeType: true
              exifOptimization: true  # Skip EXIF for duplicate hashes

            # Logging
            logging:
              level: INFO  # DEBUG, INFO, WARN, ERROR
              file: "~/.archivum/scanner.log"
              console: true
            """;

        Files.writeString(configPath, defaultConfig);
        log.info("Created default configuration file: {}", configPath);
    }

    /**
     * Merge CLI options into configuration.
     * CLI options have highest priority and override all other sources.
     */
    public static ScannerConfig mergeCliOptions(ScannerConfig config, Map<String, Object> cliOptions) {
        // Example: threads option from CLI
        if (cliOptions.containsKey("threads")) {
            config.getScanner().setThreads((Integer) cliOptions.get("threads"));
        }

        // Example: batch-size option from CLI
        if (cliOptions.containsKey("batchSize")) {
            config.getScanner().setBatchSize((Integer) cliOptions.get("batchSize"));
        }

        // Example: exclude patterns from CLI
        if (cliOptions.containsKey("exclude")) {
            @SuppressWarnings("unchecked")
            List<String> exclude = (List<String>) cliOptions.get("exclude");
            config.getScanner().getExcludePatterns().addAll(exclude);
        }

        // Example: dry-run flag from CLI
        if (cliOptions.containsKey("dryRun")) {
            config.getDryRun().setEnabled((Boolean) cliOptions.get("dryRun"));
        }

        // Example: output-dir from CLI
        if (cliOptions.containsKey("outputDir")) {
            config.getDryRun().setOutputDir((String) cliOptions.get("outputDir"));
        }

        return config;
    }
}
