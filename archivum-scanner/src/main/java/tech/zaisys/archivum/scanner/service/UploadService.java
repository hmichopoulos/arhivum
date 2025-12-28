package tech.zaisys.archivum.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.dto.CompleteScanRequest;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.SourceDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for uploading scan results to the server.
 * Shared by both standalone upload command and scan-and-upload workflow.
 */
@Slf4j
public class UploadService {

    private final String serverUrl;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public UploadService(String serverUrl, int timeoutSeconds) {
        this.serverUrl = serverUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .build();
    }

    /**
     * Upload scan results from output directory to server.
     *
     * @param outputDir Directory containing scan results
     * @return Upload result with statistics
     */
    public UploadResult upload(Path outputDir) throws IOException, InterruptedException {
        validateOutputDirectory(outputDir);

        SourceDto source = readSourceJson(outputDir);
        UUID sourceId = createSource(source);

        UploadResult result = new UploadResult();
        result.sourceId = sourceId;

        uploadBatchFiles(outputDir, sourceId, result);
        uploadCodeProjects(outputDir, sourceId, result);
        completeScan(sourceId, source);

        return result;
    }

    private void validateOutputDirectory(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            throw new IOException("Output directory does not exist: " + outputDir);
        }

        if (!Files.isDirectory(outputDir)) {
            throw new IOException("Path is not a directory: " + outputDir);
        }

        Path sourceJson = outputDir.resolve("source.json");
        if (!Files.exists(sourceJson)) {
            throw new IOException("source.json not found in " + outputDir);
        }
    }

    private SourceDto readSourceJson(Path outputDir) throws IOException {
        Path sourceJson = outputDir.resolve("source.json");
        return objectMapper.readValue(sourceJson.toFile(), SourceDto.class);
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

    private void uploadBatchFiles(Path outputDir, UUID sourceId, UploadResult result)
            throws IOException, InterruptedException {

        List<Path> batchFiles = findBatchFiles(outputDir);
        result.totalBatches = batchFiles.size();

        log.info("Found {} batch files to upload", batchFiles.size());

        for (Path batchFile : batchFiles) {
            uploadBatch(sourceId, batchFile, result);
        }
    }

    private List<Path> findBatchFiles(Path outputDir) throws IOException {
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

    private void uploadBatch(UUID sourceId, Path batchFile, UploadResult result)
            throws IOException, InterruptedException {

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

        result.uploadedBatches++;
        result.uploadedFiles += batch.getFiles().size();

        System.out.printf("Uploaded batch %d/%d (%d files)%n",
            result.uploadedBatches, result.totalBatches, batch.getFiles().size());

        log.debug("Batch {} uploaded successfully: {} files",
            batch.getBatchNumber(), batch.getFiles().size());
    }

    private void uploadCodeProjects(Path outputDir, UUID sourceId, UploadResult result)
            throws IOException, InterruptedException {

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

        result.uploadedProjects = projects.size();
        System.out.printf("Uploaded %d code project(s)%n", result.uploadedProjects);
        log.info("Code projects uploaded successfully: {} projects", result.uploadedProjects);
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

    /**
     * Result of an upload operation.
     */
    public static class UploadResult {
        public UUID sourceId;
        public int totalBatches;
        public int uploadedBatches;
        public long uploadedFiles;
        public int uploadedProjects;
    }
}
