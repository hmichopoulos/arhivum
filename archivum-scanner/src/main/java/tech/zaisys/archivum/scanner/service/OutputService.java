package tech.zaisys.archivum.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.SourceDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Service for writing scan results to JSON files (dry-run mode).
 * Organizes output into source metadata, file batches, and summary.
 */
@Slf4j
public class OutputService {

    private final Path outputDir;
    private final ObjectMapper objectMapper;
    private final Set<String> knownHashes;
    private int batchNumber = 0;

    public OutputService(Path outputDir) {
        this.outputDir = outputDir;
        this.objectMapper = createObjectMapper();
        this.knownHashes = new HashSet<>();
    }

    /**
     * Create and configure Jackson ObjectMapper.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Initialize output directory for a source.
     * Creates directory structure and writes source metadata.
     *
     * @param source Source being scanned
     * @throws IOException if directories cannot be created
     */
    public void initializeSource(SourceDto source) throws IOException {
        Path sourceDir = outputDir.resolve(source.getId().toString());
        Files.createDirectories(sourceDir);
        Files.createDirectories(sourceDir.resolve("files"));

        // Write source metadata
        Path sourcePath = sourceDir.resolve("source.json");
        objectMapper.writeValue(sourcePath.toFile(), source);

        log.info("Initialized output directory: {}", sourceDir);
    }

    /**
     * Write a batch of files to JSON.
     *
     * @param batch File batch to write
     * @throws IOException if write fails
     */
    public void writeBatch(FileBatchDto batch) throws IOException {
        Path sourceDir = outputDir.resolve(batch.getSourceId().toString());
        Path batchFile = sourceDir.resolve("files")
            .resolve(String.format("batch-%04d.json", batch.getBatchNumber()));

        objectMapper.writeValue(batchFile.toFile(), batch);

        // Track hashes for duplicate detection
        batch.getFiles().forEach(file -> knownHashes.add(file.getSha256()));

        log.debug("Wrote batch {} with {} files", batch.getBatchNumber(), batch.getFiles().size());
    }

    /**
     * Create a new batch DTO.
     *
     * @param sourceId Source ID
     * @param files Files to include in batch
     * @return FileBatchDto
     */
    public FileBatchDto createBatch(UUID sourceId, List<FileDto> files) {
        return FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(++batchNumber)
            .files(files)
            .build();
    }

    /**
     * Check if a hash has already been seen.
     * Used for duplicate detection and EXIF optimization.
     *
     * @param hash SHA-256 hash
     * @return true if hash exists, false otherwise
     */
    public boolean hashExists(String hash) {
        return knownHashes.contains(hash);
    }

    /**
     * Check multiple hashes at once.
     *
     * @param hashes List of hashes to check
     * @return Map of hash -> exists
     */
    public Map<String, Boolean> checkHashes(List<String> hashes) {
        Map<String, Boolean> results = new HashMap<>();
        for (String hash : hashes) {
            results.put(hash, knownHashes.contains(hash));
        }
        return results;
    }

    /**
     * Write scan summary when complete.
     *
     * @param sourceId Source ID
     * @param totalFiles Total files scanned
     * @param totalSize Total size in bytes
     * @param errors List of errors encountered
     * @param startTime Scan start time
     * @throws IOException if write fails
     */
    public void writeSummary(
        UUID sourceId,
        long totalFiles,
        long totalSize,
        List<ScanError> errors,
        Instant startTime
    ) throws IOException {
        Path sourceDir = outputDir.resolve(sourceId.toString());
        Path summaryPath = sourceDir.resolve("summary.json");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sourceId", sourceId);
        summary.put("totalFiles", totalFiles);
        summary.put("totalSize", totalSize);
        summary.put("totalBatches", batchNumber);
        summary.put("skippedFiles", errors.size());
        summary.put("errors", errors);
        summary.put("duration", calculateDuration(startTime));
        summary.put("startTime", startTime);
        summary.put("endTime", Instant.now());
        summary.put("scannerVersion", "0.1.0");
        summary.put("scannerHost", getHostname());
        summary.put("scannerUser", System.getProperty("user.name"));

        objectMapper.writeValue(summaryPath.toFile(), summary);

        log.info("Wrote scan summary: {} files, {} bytes", totalFiles, totalSize);
    }

    /**
     * Calculate scan duration in milliseconds.
     */
    private long calculateDuration(Instant startTime) {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Get hostname of the machine running the scanner.
     */
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Reset batch counter (used between scans).
     */
    public void reset() {
        batchNumber = 0;
        knownHashes.clear();
    }

    /**
     * Get current batch number.
     */
    public int getCurrentBatchNumber() {
        return batchNumber;
    }

    /**
     * Represents an error encountered during scanning.
     */
    public record ScanError(String file, String error) {}
}
