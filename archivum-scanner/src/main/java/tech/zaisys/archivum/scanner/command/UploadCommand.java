package tech.zaisys.archivum.scanner.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.zaisys.archivum.api.dto.*;
import tech.zaisys.archivum.api.enums.ScanStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Upload command that sends previously generated scan output to the server.
 * Useful for large disks: scan first (multi-day), then upload separately with retry.
 */
@Slf4j
@Command(
    name = "upload",
    description = "Upload previously generated scan output to server",
    mixinStandardHelpOptions = true
)
public class UploadCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Path to scan output directory (e.g., ./output/[scan-id])"
    )
    private Path outputDir;

    @Option(
        names = {"-s", "--server-url"},
        description = "Server URL (default: http://localhost:8080)",
        defaultValue = "http://localhost:8080"
    )
    private String serverUrl;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose logging"
    )
    private boolean verbose;

    @Option(
        names = {"--timeout"},
        description = "HTTP request timeout in seconds (default: 60)",
        defaultValue = "60"
    )
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private HttpClient httpClient;
    private int uploadedBatches = 0;
    private int totalBatches = 0;
    private long uploadedFiles = 0;
    private int uploadedProjects = 0;
    private long startTime;

    @Override
    public Integer call() {
        try {
            startTime = System.currentTimeMillis();

            configureLogging();

            if (!validateOutputDirectory()) {
                return 1;
            }

            printHeader();

            // Initialize HTTP client
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

            // Read source metadata
            SourceDto source = readSourceJson();
            log.info("Source: {} ({})", source.getName(), source.getType());

            // Create source on server
            UUID sourceId = createSource(source);
            log.info("Created source on server with ID: {}", sourceId);

            // Upload batch files
            List<Path> batchFiles = findBatchFiles();
            totalBatches = batchFiles.size();
            log.info("Found {} batch files to upload", totalBatches);

            for (Path batchFile : batchFiles) {
                uploadBatch(sourceId, batchFile);
            }

            // Upload code projects if they exist
            uploadCodeProjects(sourceId);

            // Complete scan
            completeScan(sourceId, source);

            printSummary();

            return 0;

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void configureLogging() {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
        }
    }

    private boolean validateOutputDirectory() {
        if (!Files.exists(outputDir)) {
            System.err.println("Error: Output directory does not exist: " + outputDir);
            return false;
        }

        if (!Files.isDirectory(outputDir)) {
            System.err.println("Error: Path is not a directory: " + outputDir);
            return false;
        }

        Path sourceJson = outputDir.resolve("source.json");
        if (!Files.exists(sourceJson)) {
            System.err.println("Error: source.json not found in " + outputDir);
            return false;
        }

        return true;
    }

    private void printHeader() {
        System.out.println("Archivum Scanner - Upload Mode");
        System.out.println("==============================");
        System.out.println("Output Dir: " + outputDir.toAbsolutePath());
        System.out.println("Server URL: " + serverUrl);
        System.out.println();
    }

    private SourceDto readSourceJson() throws IOException {
        Path sourceJson = outputDir.resolve("source.json");
        return objectMapper.readValue(sourceJson.toFile(), SourceDto.class);
    }

    private List<Path> findBatchFiles() throws IOException {
        List<Path> batchFiles = new ArrayList<>();
        Path filesDir = outputDir.resolve("files");

        if (!Files.exists(filesDir)) {
            log.warn("No files directory found - empty scan?");
            return batchFiles;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(filesDir, "batch-*.json")) {
            stream.forEach(batchFiles::add);
        }

        // Sort by batch number
        batchFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));

        return batchFiles;
    }

    private UUID createSource(SourceDto source) throws IOException, InterruptedException {
        String endpoint = serverUrl + "/api/sources";

        String requestBody = objectMapper.writeValueAsString(source);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new IOException("Failed to create source. Status: " + response.statusCode() +
                ", Response: " + response.body());
        }

        SourceDto createdSource = objectMapper.readValue(response.body(), SourceDto.class);
        return createdSource.getId();
    }

    private void uploadBatch(UUID sourceId, Path batchFile) throws IOException, InterruptedException {
        FileBatchDto batch = objectMapper.readValue(batchFile.toFile(), FileBatchDto.class);

        // Update source ID (original might be different)
        batch.setSourceId(sourceId);

        String endpoint = serverUrl + "/api/files/batch";
        String requestBody = objectMapper.writeValueAsString(batch);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new IOException("Failed to upload batch " + batch.getBatchNumber() +
                ". Status: " + response.statusCode() + ", Response: " + response.body());
        }

        uploadedBatches++;
        uploadedFiles += batch.getFiles().size();

        System.out.printf("Uploaded batch %d/%d (%d files)%n",
            uploadedBatches, totalBatches, batch.getFiles().size());

        log.debug("Batch {} uploaded successfully: {} files",
            batch.getBatchNumber(), batch.getFiles().size());
    }

    private void uploadCodeProjects(UUID sourceId) throws IOException, InterruptedException {
        Path projectsFile = outputDir.resolve("code-projects.json");

        if (!Files.exists(projectsFile)) {
            log.debug("No code-projects.json found - skipping project upload");
            return;
        }

        log.info("Uploading code projects...");

        // Read projects from file
        CodeProjectDto[] projectsArray = objectMapper.readValue(
            projectsFile.toFile(),
            CodeProjectDto[].class
        );
        List<CodeProjectDto> projects = List.of(projectsArray);

        if (projects.isEmpty()) {
            log.info("No code projects to upload");
            return;
        }

        // Update source IDs (original might be different)
        List<CodeProjectDto> updatedProjects = new ArrayList<>();
        for (CodeProjectDto p : projects) {
            updatedProjects.add(CodeProjectDto.builder()
                .sourceId(sourceId)
                .rootPath(p.getRootPath())
                .identity(p.getIdentity())
                .scannedAt(p.getScannedAt())
                .sourceFileCount(p.getSourceFileCount())
                .totalFileCount(p.getTotalFileCount())
                .totalSizeBytes(p.getTotalSizeBytes())
                .contentHash(p.getContentHash())
                .build());
        }

        String endpoint = serverUrl + "/api/code-projects/bulk";
        String requestBody = objectMapper.writeValueAsString(updatedProjects);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new IOException("Failed to upload code projects. Status: " +
                response.statusCode() + ", Response: " + response.body());
        }

        uploadedProjects = projects.size();
        System.out.printf("Uploaded %d code project(s)%n", uploadedProjects);
        log.info("Code projects uploaded successfully: {} projects", uploadedProjects);
    }

    private void completeScan(UUID sourceId, SourceDto source) throws IOException, InterruptedException {
        String endpoint = serverUrl + "/api/sources/" + sourceId + "/complete";

        CompleteScanRequest request = CompleteScanRequest.builder()
            .totalFiles(source.getTotalFiles())
            .totalSize(source.getTotalSize())
            .success(true)
            .build();

        String requestBody = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to complete scan. Status: " + response.statusCode() +
                ", Response: " + response.body());
        }

        log.info("Scan marked as complete on server");
    }

    private void printSummary() {
        long duration = System.currentTimeMillis() - startTime;

        System.out.println();
        System.out.println("Upload Complete!");
        System.out.println("================");
        System.out.println("Batches uploaded:  " + uploadedBatches);
        System.out.println("Files uploaded:    " + uploadedFiles);
        System.out.println("Projects uploaded: " + uploadedProjects);
        System.out.println("Duration:          " + formatDuration(duration));
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%dm %ds", minutes, remainingSeconds);
    }
}
