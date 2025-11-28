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

    private static final String DEFAULT_CONFIG_YAML = """
        # Archivum Scanner Configuration
        # This file configures the scanner behavior

        server:
          url: "http://localhost:8080"
          timeout: 30000

        scanner:
          threads: 0  # 0 = auto-detect CPU count
          batchSize: 1000
          skipSystemDirs: true
          followSymlinks: false
          excludePatterns:
            - ".DS_Store"
            - "Thumbs.db"
            - "*.tmp"
            - ".Trash"
            - "$RECYCLE.BIN"

        copy:
          enabled: false
          targetDir: "/staging"
          threads: 4
          verifyHash: true
          skipExisting: true

        dryRun:
          enabled: true
          outputDir: "./output"

        metadata:
          extractExif: true
          skipDuplicateExif: true

        logging:
          level: "INFO"
          file: "./scanner.log"
        """;

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

        Path configDir = configPath.getParent();
        if (configDir != null && !Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        Files.writeString(configPath, DEFAULT_CONFIG_YAML);
        log.info("Created default configuration file: {}", configPath);
    }
}
