package tech.zaisys.archivum.scanner.command;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.PhysicalId;
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

        System.out.println("Archivum Scanner v0.1.0");
        System.out.println("======================");
        System.out.println("Path:   " + scanPath.toAbsolutePath());
        System.out.println();

        // Detect physical device identifiers
        System.out.println("Detecting physical device identifiers...");
        PhysicalIdDetector physicalIdDetector = new PhysicalIdDetector();
        PhysicalId physicalId = physicalIdDetector.detect(scanPath);

        // Interactive prompts for source name, physical label, notes
        InteractivePrompt prompt = new InteractivePrompt();
        String sourceNameResolved;
        if (sourceName != null) {
            sourceNameResolved = sourceName;
        } else {
            sourceNameResolved = prompt.promptForSourceName(physicalId.getVolumeLabel());
        }

        String physicalLabel = prompt.promptForPhysicalLabel();
        String notes = prompt.promptForNotes();
        prompt.close();

        // Update PhysicalId with user-provided information
        physicalId = PhysicalId.builder()
            .diskUuid(physicalId.getDiskUuid())
            .partitionUuid(physicalId.getPartitionUuid())
            .volumeLabel(physicalId.getVolumeLabel())
            .serialNumber(physicalId.getSerialNumber())
            .mountPoint(physicalId.getMountPoint())
            .filesystemType(physicalId.getFilesystemType())
            .capacity(physicalId.getCapacity())
            .usedSpace(physicalId.getUsedSpace())
            .physicalLabel(physicalLabel)
            .notes(notes)
            .build();

        System.out.println();
        System.out.println("Source: " + sourceNameResolved);
        System.out.println();

        // Initialize services
        int hashThreads = threads != null ? threads : config.getHashThreads();
        int fileBatchSize = batchSize != null ? batchSize : config.getBatchSize();

        try (HashService hashService = new HashService(hashThreads)) {
            FileWalkerService fileWalker = new FileWalkerService(config);
            MetadataService metadataService = new MetadataService();
            OutputService outputService = new OutputService(outputPath);

            // Create source with physical ID
            SourceDto source = createSource(sourceNameResolved, physicalId);
            outputService.initializeSource(source);

            // Discover files
            System.out.println("Discovering files...");
            FileWalkerService.WalkResult walkResult = fileWalker.walk(scanPath);
            List<Path> files = walkResult.files();
            long totalSize = walkResult.totalSize();

            if (files.isEmpty()) {
                System.out.println("No files found to scan.");
                return 0;
            }

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
                    // Compute hash with progress callback for large files
                    String hash = hashService.computeHash(file, (bytesProcessed, totalBytes) -> {
                        progress.updateFileProgress(file.toString(), bytesProcessed, totalBytes);
                    });

                    // Check if ALREADY exists (before registering)
                    boolean isDuplicate = outputService.hashExists(hash);

                    // Register hash immediately for future duplicate detection
                    outputService.registerHash(hash);

                    // Determine if EXIF should be extracted
                    // Skip EXIF if: disabled OR (optimization enabled AND duplicate)
                    boolean shouldExtractExif = config.isExifExtractionEnabled()
                        && (!config.isExifOptimizationEnabled() || !isDuplicate);

                    // Extract metadata with optional EXIF
                    FileDto fileDto = metadataService.extractMetadata(file, source.getId(), hash, shouldExtractExif);

                    // Mark as duplicate
                    fileDto.setIsDuplicate(isDuplicate);

                    // Add to batch
                    currentBatch.add(fileDto);

                    // Update progress
                    progress.updateProgress(file.toString(), fileDto.getSize());

                    // Write batch if full
                    if (currentBatch.size() >= fileBatchSize) {
                        writeBatch(outputService, source.getId(), currentBatch);
                        currentBatch.clear();
                    }

                } catch (IOException e) {
                    log.warn("Cannot read file: {} - {}", file, e.getMessage());
                    errors.add(new OutputService.ScanError(
                        file.toString(),
                        "IO Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    ));
                } catch (RuntimeException e) {
                    log.error("Unexpected error processing file: {}", file, e);
                    errors.add(new OutputService.ScanError(
                        file.toString(),
                        "Runtime Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    ));
                    // Let critical errors (OutOfMemoryError, etc.) propagate
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
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Results saved to:");
            System.out.println("  " + outputPath.toAbsolutePath().resolve(source.getId().toString()));
            System.out.println();
            System.out.println("Quick access:");
            System.out.println("  Source info:   " + outputPath.toAbsolutePath().resolve(source.getId().toString()).resolve("source.json"));
            System.out.println("  File data:     " + outputPath.toAbsolutePath().resolve(source.getId().toString()).resolve("files/"));
            System.out.println("  Summary:       " + outputPath.toAbsolutePath().resolve(source.getId().toString()).resolve("summary.json"));
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if (!errors.isEmpty()) {
                System.out.println();
                System.out.println("⚠ Warnings: " + errors.size() + " files could not be processed");
                System.out.println("  See summary.json for details");
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
    private SourceDto createSource(String name, PhysicalId physicalId) {
        return SourceDto.builder()
            .id(UUID.randomUUID())
            .name(name)
            .type(SourceType.DISK)
            .rootPath(scanPath.toAbsolutePath().toString())
            .physicalId(physicalId)
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
     * Write a batch to output.
     */
    private void writeBatch(OutputService outputService, UUID sourceId, List<FileDto> files)
            throws IOException {
        FileBatchDto batch = outputService.createBatch(sourceId, files);
        outputService.writeBatch(batch);
    }
}
