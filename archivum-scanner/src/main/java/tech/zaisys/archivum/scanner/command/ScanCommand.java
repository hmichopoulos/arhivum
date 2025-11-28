package tech.zaisys.archivum.scanner.command;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.scanner.config.ConfigLoader;
import tech.zaisys.archivum.scanner.config.ScannerConfig;
import tech.zaisys.archivum.scanner.service.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Main scan command that orchestrates the file scanning process.
 * Walks directory tree, hashes files, extracts metadata, and outputs results.
 */
@Slf4j
@Command(
    name = "scan",
    description = "Scan a directory and catalog all files",
    mixinStandardHelpOptions = true
)
public class ScanCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Path to scan (directory or mount point)"
    )
    private Path scanPath;

    @Option(
        names = {"-n", "--name"},
        description = "Logical name for this source (e.g., 'WD 4TB Blue')"
    )
    private String sourceName;

    @Option(
        names = {"-o", "--output"},
        description = "Output directory for JSON files (dry-run mode)",
        defaultValue = "./output"
    )
    private Path outputPath;

    @Option(
        names = {"-c", "--config"},
        description = "Path to configuration file"
    )
    private Path configPath;

    @Option(
        names = {"--threads"},
        description = "Number of threads for hashing (0 = auto-detect)"
    )
    private Integer threads;

    @Option(
        names = {"--batch-size"},
        description = "Number of files per batch"
    )
    private Integer batchSize;

    @Override
    public Integer call() throws Exception {
        Instant startTime = Instant.now();

        // Load configuration
        ScannerConfig config = loadConfiguration();

        // Validate scan path
        if (!Files.exists(scanPath)) {
            System.err.println("Error: Path does not exist: " + scanPath);
            return 1;
        }

        if (!Files.isDirectory(scanPath)) {
            System.err.println("Error: Path is not a directory: " + scanPath);
            return 1;
        }

        // Determine source name
        String sourceNameResolved = sourceName != null
            ? sourceName
            : scanPath.getFileName().toString();

        System.out.println("Archivum Scanner v0.1.0");
        System.out.println("======================");
        System.out.println("Source: " + sourceNameResolved);
        System.out.println("Path:   " + scanPath.toAbsolutePath());
        System.out.println();

        // Initialize services
        int hashThreads = threads != null ? threads : config.getHashThreads();
        int fileBatchSize = batchSize != null ? batchSize : config.getBatchSize();

        try (HashService hashService = new HashService(hashThreads)) {
            FileWalkerService fileWalker = new FileWalkerService(config);
            MetadataService metadataService = new MetadataService();
            OutputService outputService = new OutputService(outputPath);

            // Create source
            SourceDto source = createSource(sourceNameResolved);
            outputService.initializeSource(source);

            // Discover files
            System.out.println("Discovering files...");
            List<Path> files = fileWalker.walk(scanPath);

            if (files.isEmpty()) {
                System.out.println("No files found to scan.");
                return 0;
            }

            // Calculate total size
            long totalSize = calculateTotalSize(files);

            // Initialize progress reporter
            ProgressReporter progress = new ProgressReporter(
                sourceNameResolved,
                files.size(),
                totalSize
            );

            // Process files
            List<FileDto> currentBatch = new ArrayList<>();
            List<OutputService.ScanError> errors = new ArrayList<>();

            for (Path file : files) {
                try {
                    // Compute hash
                    String hash = hashService.computeHash(file);

                    // Extract metadata
                    FileDto fileDto = metadataService.extractMetadata(file, source.getId(), hash);

                    // Check for duplicates
                    if (outputService.hashExists(hash)) {
                        fileDto.setIsDuplicate(true);
                    }

                    // Add to batch
                    currentBatch.add(fileDto);

                    // Update progress
                    progress.updateProgress(file.toString(), fileDto.getSize());

                    // Write batch if full
                    if (currentBatch.size() >= fileBatchSize) {
                        writeBatch(outputService, source.getId(), currentBatch);
                        currentBatch.clear();
                    }

                } catch (Exception e) {
                    log.error("Failed to process file: {}", file, e);
                    errors.add(new OutputService.ScanError(
                        file.toString(),
                        e.getMessage()
                    ));
                }
            }

            // Write remaining files
            if (!currentBatch.isEmpty()) {
                writeBatch(outputService, source.getId(), currentBatch);
            }

            // Complete progress
            progress.complete();

            // Write summary
            outputService.writeSummary(
                source.getId(),
                files.size() - errors.size(),
                totalSize,
                errors,
                startTime
            );

            // Print results
            System.out.println();
            System.out.println("Output directory: " + outputPath.toAbsolutePath());

            if (!errors.isEmpty()) {
                System.out.println("Warnings: " + errors.size() + " files could not be processed");
                System.out.println("See summary.json for details");
            }

            return 0;

        } catch (Exception e) {
            log.error("Scan failed", e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Load configuration from file or defaults, applying CLI overrides.
     */
    private ScannerConfig loadConfiguration() throws IOException {
        ScannerConfig config;

        if (configPath != null) {
            config = ConfigLoader.loadFromFile(configPath);
        } else {
            config = ConfigLoader.load();
        }

        return config;
    }

    /**
     * Create a SourceDto for this scan.
     */
    private SourceDto createSource(String name) {
        return SourceDto.builder()
            .id(UUID.randomUUID())
            .name(name)
            .type(SourceType.DISK)
            .rootPath(scanPath.toAbsolutePath().toString())
            .status(ScanStatus.SCANNING)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .scanStartedAt(Instant.now())
            .postponed(false)
            .build();
    }

    /**
     * Calculate total size of all files.
     */
    private long calculateTotalSize(List<Path> files) {
        long total = 0;
        for (Path file : files) {
            try {
                total += Files.size(file);
            } catch (IOException e) {
                log.warn("Cannot get size of: {}", file);
            }
        }
        return total;
    }

    /**
     * Write a batch to output.
     */
    private void writeBatch(OutputService outputService, UUID sourceId, List<FileDto> files)
            throws IOException {
        FileBatchDto batch = outputService.createBatch(sourceId, new ArrayList<>(files));
        outputService.writeBatch(batch);
    }
}
