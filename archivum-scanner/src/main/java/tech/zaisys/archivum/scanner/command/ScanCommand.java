package tech.zaisys.archivum.scanner.command;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

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

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose (DEBUG) logging"
    )
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        Instant startTime = Instant.now();

        configureLogging();
        ScannerConfig config = loadConfiguration();

        if (!validateScanPath()) {
            return 1;
        }

        printHeader();

        PhysicalId physicalId = detectAndConfigurePhysicalId();
        String sourceNameResolved = resolveSourceName(physicalId);

        try (HashService hashService = createHashService(config)) {
            ScanContext context = initializeScanContext(config, hashService, sourceNameResolved, physicalId);

            List<Path> files = discoverFiles(context);
            if (files.isEmpty()) {
                System.out.println("No files found to scan.");
                return 0;
            }

            Map<Path, String> fileHashMap = processFiles(context, files);
            scanCodeProjects(context, fileHashMap);

            writeSummary(context, files.size(), startTime);
            printResults(context);

            return 0;
        } catch (Exception e) {
            handleScanError(e);
            return 1;
        }
    }

    private void configureLogging() {
        if (verbose) {
            setLogLevel("DEBUG");
        }
    }

    private boolean validateScanPath() {
        if (!Files.exists(scanPath)) {
            System.err.println("Error: Path does not exist: " + scanPath);
            return false;
        }

        if (!Files.isDirectory(scanPath)) {
            System.err.println("Error: Path is not a directory: " + scanPath);
            return false;
        }

        return true;
    }

    private void printHeader() {
        System.out.println("Archivum Scanner v0.1.0");
        System.out.println("======================");
        System.out.println("Path:   " + scanPath.toAbsolutePath());
        System.out.println();
    }

    /**
     * Detect physical device identifiers and gather user input.
     *
     * @return PhysicalId with detected and user-provided information
     */
    private PhysicalId detectAndConfigurePhysicalId() {
        System.out.println("Detecting physical device identifiers...");
        PhysicalIdDetector detector = new PhysicalIdDetector();
        PhysicalId detected = detector.detect(scanPath);

        InteractivePrompt prompt = new InteractivePrompt();
        String physicalLabel = prompt.promptForPhysicalLabel();
        String notes = prompt.promptForNotes();
        prompt.close();

        return PhysicalId.builder()
            .diskUuid(detected.getDiskUuid())
            .partitionUuid(detected.getPartitionUuid())
            .volumeLabel(detected.getVolumeLabel())
            .serialNumber(detected.getSerialNumber())
            .mountPoint(detected.getMountPoint())
            .filesystemType(detected.getFilesystemType())
            .capacity(detected.getCapacity())
            .usedSpace(detected.getUsedSpace())
            .physicalLabel(physicalLabel)
            .notes(notes)
            .build();
    }

    /**
     * Resolve source name from CLI option or interactive prompt.
     *
     * @param physicalId Physical ID with volume label
     * @return Resolved source name
     */
    private String resolveSourceName(PhysicalId physicalId) {
        if (sourceName != null) {
            System.out.println();
            System.out.println("Source: " + sourceName);
            System.out.println();
            return sourceName;
        }

        InteractivePrompt prompt = new InteractivePrompt();
        String name = prompt.promptForSourceName(physicalId.getVolumeLabel());
        prompt.close();

        System.out.println();
        System.out.println("Source: " + name);
        System.out.println();

        return name;
    }

    /**
     * Create hash service with configured thread count.
     *
     * @param config Scanner configuration
     * @return Hash service instance
     */
    private HashService createHashService(ScannerConfig config) {
        int hashThreads = threads != null ? threads : config.getHashThreads();
        return new HashService(hashThreads);
    }

    /**
     * Initialize scan context with all required services and state.
     *
     * @param config Scanner configuration
     * @param hashService Hash service instance
     * @param sourceNameResolved Resolved source name
     * @param physicalId Physical device identifiers
     * @return Initialized scan context
     */
    private ScanContext initializeScanContext(
            ScannerConfig config,
            HashService hashService,
            String sourceNameResolved,
            PhysicalId physicalId) throws IOException {

        MetadataService metadataService = new MetadataService();
        OutputService outputService = new OutputService(outputPath);

        SourceDto source = createSource(sourceNameResolved, physicalId);
        outputService.initializeSource(source);

        int fileBatchSize = batchSize != null ? batchSize : config.getBatchSize();

        return new ScanContext(
            outputService,
            source,
            metadataService,
            hashService,
            null, // Progress reporter created after file discovery
            config,
            fileBatchSize,
            new ArrayList<>(),
            new ArrayList<>(),
            0L // Total size set during discovery
        );
    }

    /**
     * Discover all files in the scan path.
     *
     * @param context Scan context
     * @return List of discovered files
     */
    private List<Path> discoverFiles(ScanContext context) throws IOException {
        System.out.println("Discovering files...");
        FileWalkerService fileWalker = new FileWalkerService(context.config);
        FileWalkerService.WalkResult walkResult = fileWalker.walk(scanPath);

        List<Path> files = walkResult.files();
        long totalSize = walkResult.totalSize();

        // Update context with total size and create progress reporter
        context.totalSize = totalSize;
        context.progress = new ProgressReporter(
            context.source.getName(),
            files.size(),
            totalSize
        );

        return files;
    }

    /**
     * Process all discovered files.
     *
     * @param context Scan context
     * @param files List of files to process
     * @return Map of file paths to their hashes
     */
    private Map<Path, String> processFiles(ScanContext context, List<Path> files) throws IOException {
        Map<Path, String> fileHashMap = new ConcurrentHashMap<>();

        for (Path file : files) {
            processFile(context, file, fileHashMap);
        }

        // Write remaining batch
        if (!context.currentBatch.isEmpty()) {
            writeBatch(context.outputService, context.source.getId(), context.currentBatch);
        }

        context.progress.complete();
        return fileHashMap;
    }

    /**
     * Process a single file.
     *
     * @param context Scan context
     * @param file File to process
     * @param fileHashMap Map to store file hash
     */
    private void processFile(ScanContext context, Path file, Map<Path, String> fileHashMap) {
        try {
            String hash = computeFileHash(context, file);
            boolean isDuplicate = context.outputService.hashExists(hash);
            context.outputService.registerHash(hash);
            fileHashMap.put(file, hash);

            FileDto fileDto = extractFileMetadata(context, file, hash, isDuplicate);
            addToBatch(context, fileDto);

        } catch (IOException e) {
            recordError(context, file, "IO Error", e);
        } catch (RuntimeException e) {
            recordError(context, file, "Runtime Error", e);
        }
    }

    /**
     * Compute hash for a file with progress reporting.
     *
     * @param context Scan context
     * @param file File to hash
     * @return File hash
     */
    private String computeFileHash(ScanContext context, Path file) throws IOException {
        return context.hashService.computeHash(file, (bytesProcessed, totalBytes) -> {
            context.progress.updateFileProgress(file.toString(), bytesProcessed, totalBytes);
        });
    }

    /**
     * Extract metadata for a file.
     *
     * @param context Scan context
     * @param file File to extract metadata from
     * @param hash File hash
     * @param isDuplicate Whether file is a duplicate
     * @return File DTO with metadata
     */
    private FileDto extractFileMetadata(ScanContext context, Path file, String hash, boolean isDuplicate)
            throws IOException {

        boolean shouldExtractExif = context.config.isExifExtractionEnabled()
            && (!context.config.isExifOptimizationEnabled() || !isDuplicate);

        FileDto fileDto = context.metadataService.extractMetadata(
            file, context.source.getId(), hash, shouldExtractExif);

        fileDto.setIsDuplicate(isDuplicate);
        return fileDto;
    }

    /**
     * Add file to current batch and write if full.
     *
     * @param context Scan context
     * @param fileDto File DTO to add
     */
    private void addToBatch(ScanContext context, FileDto fileDto) throws IOException {
        context.currentBatch.add(fileDto);
        context.progress.updateProgress(fileDto.getPath(), fileDto.getSize());

        if (context.currentBatch.size() >= context.batchSize) {
            writeBatch(context.outputService, context.source.getId(), context.currentBatch);
            context.currentBatch.clear();
        }
    }

    /**
     * Record an error for a file.
     *
     * @param context Scan context
     * @param file File that caused error
     * @param errorType Error type description
     * @param e Exception that occurred
     */
    private void recordError(ScanContext context, Path file, String errorType, Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        if (e instanceof IOException) {
            log.warn("Cannot read file: {} - {}", file, message);
        } else {
            log.error("Unexpected error processing file: {}", file, e);
        }

        context.errors.add(new OutputService.ScanError(
            file.toString(),
            errorType + ": " + message
        ));
    }

    /**
     * Scan for code projects in the scanned directory.
     *
     * @param context Scan context
     * @param fileHashMap Map of file paths to hashes
     */
    private void scanCodeProjects(ScanContext context, Map<Path, String> fileHashMap) throws IOException {
        System.out.println();
        System.out.println("Scanning for code projects...");

        CodeProjectScannerService scanner = new CodeProjectScannerService();
        CodeProjectScannerService.ScanResult result =
            scanner.scanForProjects(scanPath, context.source.getId(), fileHashMap);

        List<CodeProjectDto> projects = result.projects();

        if (!projects.isEmpty()) {
            printCodeProjects(projects);
            context.outputService.writeCodeProjects(context.source.getId(), projects);
        } else {
            System.out.println("No code projects detected.");
        }
    }

    /**
     * Print discovered code projects.
     *
     * @param projects List of code projects
     */
    private void printCodeProjects(List<CodeProjectDto> projects) {
        System.out.println("Found " + projects.size() + " code project(s):");
        for (CodeProjectDto project : projects) {
            System.out.println("  - " + project.getIdentity().getIdentifier() +
                " (" + project.getIdentity().getType().getDisplayName() + ") at " +
                project.getRootPath());
        }
    }

    /**
     * Write scan summary.
     *
     * @param context Scan context
     * @param totalFiles Total number of files scanned
     * @param startTime Scan start time
     */
    private void writeSummary(ScanContext context, int totalFiles, Instant startTime) throws IOException {
        context.outputService.writeSummary(
            context.source.getId(),
            totalFiles - context.errors.size(),
            context.totalSize,
            context.errors,
            startTime
        );
    }

    /**
     * Print scan results summary.
     *
     * @param context Scan context
     */
    private void printResults(ScanContext context) {
        Path sourceOutputDir = outputPath.toAbsolutePath().resolve(context.source.getId().toString());

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Results saved to:");
        System.out.println("  " + sourceOutputDir);
        System.out.println();
        System.out.println("Quick access:");
        System.out.println("  Source info:   " + sourceOutputDir.resolve("source.json"));
        System.out.println("  File data:     " + sourceOutputDir.resolve("files/"));
        System.out.println("  Summary:       " + sourceOutputDir.resolve("summary.json"));
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!context.errors.isEmpty()) {
            System.out.println();
            System.out.println("⚠ Warnings: " + context.errors.size() + " files could not be processed");
            System.out.println("  See summary.json for details");
        }
    }

    /**
     * Handle scan error.
     *
     * @param e Exception that caused scan to fail
     */
    private void handleScanError(Exception e) {
        log.error("Scan failed", e);
        System.err.println("Error: " + e.getMessage());
    }

    /**
     * Configure logging level based on verbose flag.
     */
    private void configureLogging() {
        if (verbose) {
            setLogLevel("DEBUG");
        }
    }

    /**
     * Validate that scan path exists and is a directory.
     *
     * @return true if valid, false otherwise
     */
    private boolean validateScanPath() {
        if (!Files.exists(scanPath)) {
            System.err.println("Error: Path does not exist: " + scanPath);
            return false;
        }

        if (!Files.isDirectory(scanPath)) {
            System.err.println("Error: Path is not a directory: " + scanPath);
            return false;
        }

        return true;
    }

    /**
     * Print scanner header with version and path.
     */
    private void printHeader() {
        System.out.println("Archivum Scanner v0.1.0");
        System.out.println("======================");
        System.out.println("Path:   " + scanPath.toAbsolutePath());
        System.out.println();
    }

    /**
     * Detect physical device identifiers and gather user input.
     *
     * @return PhysicalId with detected and user-provided information
     */
    private PhysicalId detectAndConfigurePhysicalId() {
        System.out.println("Detecting physical device identifiers...");
        PhysicalIdDetector detector = new PhysicalIdDetector();
        PhysicalId detected = detector.detect(scanPath);

        InteractivePrompt prompt = new InteractivePrompt();
        String physicalLabel = prompt.promptForPhysicalLabel();
        String notes = prompt.promptForNotes();
        prompt.close();

        return PhysicalId.builder()
            .diskUuid(detected.getDiskUuid())
            .partitionUuid(detected.getPartitionUuid())
            .volumeLabel(detected.getVolumeLabel())
            .serialNumber(detected.getSerialNumber())
            .mountPoint(detected.getMountPoint())
            .filesystemType(detected.getFilesystemType())
            .capacity(detected.getCapacity())
            .usedSpace(detected.getUsedSpace())
            .physicalLabel(physicalLabel)
            .notes(notes)
            .build();
    }

    /**
     * Resolve source name from CLI option or interactive prompt.
     *
     * @param physicalId Physical ID with volume label
     * @return Resolved source name
     */
    private String resolveSourceName(PhysicalId physicalId) {
        if (sourceName != null) {
            System.out.println();
            System.out.println("Source: " + sourceName);
            System.out.println();
            return sourceName;
        }

        InteractivePrompt prompt = new InteractivePrompt();
        String name = prompt.promptForSourceName(physicalId.getVolumeLabel());
        prompt.close();

        System.out.println();
        System.out.println("Source: " + name);
        System.out.println();

        return name;
    }

    /**
     * Create hash service with configured thread count.
     *
     * @param config Scanner configuration
     * @return Hash service instance
     */
    private HashService createHashService(ScannerConfig config) {
        int hashThreads = threads != null ? threads : config.getHashThreads();
        return new HashService(hashThreads);
    }

    /**
     * Initialize scan context with all required services and state.
     *
     * @param config Scanner configuration
     * @param hashService Hash service instance
     * @param sourceNameResolved Resolved source name
     * @param physicalId Physical device identifiers
     * @return Initialized scan context
     */
    private ScanContext initializeScanContext(
            ScannerConfig config,
            HashService hashService,
            String sourceNameResolved,
            PhysicalId physicalId) throws IOException {

        MetadataService metadataService = new MetadataService();
        OutputService outputService = new OutputService(outputPath);

        SourceDto source = createSource(sourceNameResolved, physicalId);
        outputService.initializeSource(source);

        int fileBatchSize = batchSize != null ? batchSize : config.getBatchSize();

        return new ScanContext(
            outputService,
            source,
            metadataService,
            hashService,
            null, // Progress reporter created after file discovery
            config,
            fileBatchSize,
            new ArrayList<>(),
            new ArrayList<>(),
            0L // Total size set during discovery
        );
    }

    /**
     * Discover all files in the scan path.
     *
     * @param context Scan context
     * @return List of discovered files
     */
    private List<Path> discoverFiles(ScanContext context) throws IOException {
        System.out.println("Discovering files...");
        FileWalkerService fileWalker = new FileWalkerService(context.config);
        FileWalkerService.WalkResult walkResult = fileWalker.walk(scanPath);

        List<Path> files = walkResult.files();
        long totalSize = walkResult.totalSize();

        // Update context with total size and create progress reporter
        context.totalSize = totalSize;
        context.progress = new ProgressReporter(
            context.source.getName(),
            files.size(),
            totalSize
        );

        return files;
    }

    /**
     * Process all discovered files.
     *
     * @param context Scan context
     * @param files List of files to process
     * @return Map of file paths to their hashes
     */
    private Map<Path, String> processFiles(ScanContext context, List<Path> files) throws IOException {
        Map<Path, String> fileHashMap = new ConcurrentHashMap<>();

        for (Path file : files) {
            processFile(context, file, fileHashMap);
        }

        // Write remaining batch
        if (!context.currentBatch.isEmpty()) {
            writeBatch(context.outputService, context.source.getId(), context.currentBatch);
        }

        context.progress.complete();
        return fileHashMap;
    }

    /**
     * Process a single file.
     *
     * @param context Scan context
     * @param file File to process
     * @param fileHashMap Map to store file hash
     */
    private void processFile(ScanContext context, Path file, Map<Path, String> fileHashMap) {
        try {
            String hash = computeFileHash(context, file);
            boolean isDuplicate = context.outputService.hashExists(hash);
            context.outputService.registerHash(hash);
            fileHashMap.put(file, hash);

            FileDto fileDto = extractFileMetadata(context, file, hash, isDuplicate);
            addToBatch(context, fileDto);

        } catch (IOException e) {
            recordError(context, file, "IO Error", e);
        } catch (RuntimeException e) {
            recordError(context, file, "Runtime Error", e);
        }
    }

    /**
     * Compute hash for a file with progress reporting.
     *
     * @param context Scan context
     * @param file File to hash
     * @return File hash
     */
    private String computeFileHash(ScanContext context, Path file) throws IOException {
        return context.hashService.computeHash(file, (bytesProcessed, totalBytes) -> {
            context.progress.updateFileProgress(file.toString(), bytesProcessed, totalBytes);
        });
    }

    /**
     * Extract metadata for a file.
     *
     * @param context Scan context
     * @param file File to extract metadata from
     * @param hash File hash
     * @param isDuplicate Whether file is a duplicate
     * @return File DTO with metadata
     */
    private FileDto extractFileMetadata(ScanContext context, Path file, String hash, boolean isDuplicate)
            throws IOException {

        boolean shouldExtractExif = context.config.isExifExtractionEnabled()
            && (!context.config.isExifOptimizationEnabled() || !isDuplicate);

        FileDto fileDto = context.metadataService.extractMetadata(
            file, context.source.getId(), hash, shouldExtractExif);

        fileDto.setIsDuplicate(isDuplicate);
        return fileDto;
    }

    /**
     * Add file to current batch and write if full.
     *
     * @param context Scan context
     * @param fileDto File DTO to add
     */
    private void addToBatch(ScanContext context, FileDto fileDto) throws IOException {
        context.currentBatch.add(fileDto);
        context.progress.updateProgress(fileDto.getPath(), fileDto.getSize());

        if (context.currentBatch.size() >= context.batchSize) {
            writeBatch(context.outputService, context.source.getId(), context.currentBatch);
            context.currentBatch.clear();
        }
    }

    /**
     * Record an error for a file.
     *
     * @param context Scan context
     * @param file File that caused error
     * @param errorType Error type description
     * @param e Exception that occurred
     */
    private void recordError(ScanContext context, Path file, String errorType, Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        if (e instanceof IOException) {
            log.warn("Cannot read file: {} - {}", file, message);
        } else {
            log.error("Unexpected error processing file: {}", file, e);
        }

        context.errors.add(new OutputService.ScanError(
            file.toString(),
            errorType + ": " + message
        ));
    }

    /**
     * Scan for code projects in the scanned directory.
     *
     * @param context Scan context
     * @param fileHashMap Map of file paths to hashes
     */
    private void scanCodeProjects(ScanContext context, Map<Path, String> fileHashMap) throws IOException {
        System.out.println();
        System.out.println("Scanning for code projects...");

        CodeProjectScannerService scanner = new CodeProjectScannerService();
        CodeProjectScannerService.ScanResult result =
            scanner.scanForProjects(scanPath, context.source.getId(), fileHashMap);

        List<CodeProjectDto> projects = result.projects();

        if (!projects.isEmpty()) {
            printCodeProjects(projects);
            context.outputService.writeCodeProjects(context.source.getId(), projects);
        } else {
            System.out.println("No code projects detected.");
        }
    }

    /**
     * Print discovered code projects.
     *
     * @param projects List of code projects
     */
    private void printCodeProjects(List<CodeProjectDto> projects) {
        System.out.println("Found " + projects.size() + " code project(s):");
        for (CodeProjectDto project : projects) {
            System.out.println("  - " + project.getIdentity().getIdentifier() +
                " (" + project.getIdentity().getType().getDisplayName() + ") at " +
                project.getRootPath());
        }
    }

    /**
     * Write scan summary.
     *
     * @param context Scan context
     * @param totalFiles Total number of files scanned
     * @param startTime Scan start time
     */
    private void writeSummary(ScanContext context, int totalFiles, Instant startTime) throws IOException {
        context.outputService.writeSummary(
            context.source.getId(),
            totalFiles - context.errors.size(),
            context.totalSize,
            context.errors,
            startTime
        );
    }

    /**
     * Print scan results summary.
     *
     * @param context Scan context
     */
    private void printResults(ScanContext context) {
        Path sourceOutputDir = outputPath.toAbsolutePath().resolve(context.source.getId().toString());

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Results saved to:");
        System.out.println("  " + sourceOutputDir);
        System.out.println();
        System.out.println("Quick access:");
        System.out.println("  Source info:   " + sourceOutputDir.resolve("source.json"));
        System.out.println("  File data:     " + sourceOutputDir.resolve("files/"));
        System.out.println("  Summary:       " + sourceOutputDir.resolve("summary.json"));
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!context.errors.isEmpty()) {
            System.out.println();
            System.out.println("⚠ Warnings: " + context.errors.size() + " files could not be processed");
            System.out.println("  See summary.json for details");
        }
    }

    /**
     * Handle scan error.
     *
     * @param e Exception that caused scan to fail
     */
    private void handleScanError(Exception e) {
        log.error("Scan failed", e);
        System.err.println("Error: " + e.getMessage());
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

    /**
     * Set the logging level programmatically.
     */
    private void setLogLevel(String level) {
        ch.qos.logback.classic.Logger root =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.valueOf(level));
        log.info("Log level set to {}", level);
    }

    /**
     * Context object holding state and services for a scan operation.
     */
    private static class ScanContext {
        final OutputService outputService;
        final SourceDto source;
        final MetadataService metadataService;
        final HashService hashService;
        ProgressReporter progress; // Set after file discovery
        final ScannerConfig config;
        final int batchSize;
        final List<FileDto> currentBatch;
        final List<OutputService.ScanError> errors;
        long totalSize; // Set during file discovery

        ScanContext(
                OutputService outputService,
                SourceDto source,
                MetadataService metadataService,
                HashService hashService,
                ProgressReporter progress,
                ScannerConfig config,
                int batchSize,
                List<FileDto> currentBatch,
                List<OutputService.ScanError> errors,
                long totalSize) {
            this.outputService = outputService;
            this.source = source;
            this.metadataService = metadataService;
            this.hashService = hashService;
            this.progress = progress;
            this.config = config;
            this.batchSize = batchSize;
            this.currentBatch = currentBatch;
            this.errors = errors;
            this.totalSize = totalSize;
        }
    }
}
