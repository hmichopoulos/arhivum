package tech.zaisys.archivum.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.FileStatus;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutputServiceTest {

    private OutputService outputService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        outputService = new OutputService(tempDir);
        objectMapper = new ObjectMapper();
    }

    @Test
    void initializeSource_createsDirectoryStructure() throws IOException {
        // Given
        SourceDto source = SourceDto.builder()
            .id(UUID.randomUUID())
            .name("Test Source")
            .type(SourceType.DISK)
            .rootPath("/test/path")
            .status(ScanStatus.SCANNING)
            .build();

        // When
        outputService.initializeSource(source);

        // Then
        Path sourceDir = tempDir.resolve(source.getId().toString());
        assertTrue(Files.exists(sourceDir));
        assertTrue(Files.exists(sourceDir.resolve("files")));
        assertTrue(Files.exists(sourceDir.resolve("source.json")));
    }

    @Test
    void initializeSource_writesSourceMetadata() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder()
            .id(sourceId)
            .name("Test Source")
            .type(SourceType.DISK)
            .rootPath("/test/path")
            .status(ScanStatus.SCANNING)
            .totalFiles(100L)
            .totalSize(1000000L)
            .build();

        // When
        outputService.initializeSource(source);

        // Then
        Path sourcePath = tempDir.resolve(sourceId.toString()).resolve("source.json");
        assertTrue(Files.exists(sourcePath));

        SourceDto written = objectMapper.readValue(sourcePath.toFile(), SourceDto.class);
        assertEquals(sourceId, written.getId());
        assertEquals("Test Source", written.getName());
        assertEquals(100L, written.getTotalFiles());
    }

    @Test
    void writeBatch_createsJsonFile() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder()
            .id(sourceId)
            .name("Test")
            .type(SourceType.DISK)
            .build();
        outputService.initializeSource(source);

        List<FileDto> files = List.of(
            createTestFile(sourceId, "file1.txt", "hash1"),
            createTestFile(sourceId, "file2.txt", "hash2")
        );

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(files)
            .build();

        // When
        outputService.writeBatch(batch);

        // Then
        Path batchPath = tempDir.resolve(sourceId.toString())
            .resolve("files")
            .resolve("batch-0001.json");
        assertTrue(Files.exists(batchPath));
    }

    @Test
    void writeBatch_batchNumberFormatted() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder().id(sourceId).name("Test").type(SourceType.DISK).build();
        outputService.initializeSource(source);

        List<FileDto> files = List.of(createTestFile(sourceId, "file.txt", "hash"));

        // When - write multiple batches
        for (int i = 1; i <= 15; i++) {
            FileBatchDto batch = outputService.createBatch(sourceId, files);
            outputService.writeBatch(batch);
        }

        // Then
        Path filesDir = tempDir.resolve(sourceId.toString()).resolve("files");
        assertTrue(Files.exists(filesDir.resolve("batch-0001.json")));
        assertTrue(Files.exists(filesDir.resolve("batch-0010.json")));
        assertTrue(Files.exists(filesDir.resolve("batch-0015.json")));
    }

    @Test
    void createBatch_incrementsBatchNumber() {
        // Given
        UUID sourceId = UUID.randomUUID();
        List<FileDto> files = new ArrayList<>();

        // When
        FileBatchDto batch1 = outputService.createBatch(sourceId, files);
        FileBatchDto batch2 = outputService.createBatch(sourceId, files);
        FileBatchDto batch3 = outputService.createBatch(sourceId, files);

        // Then
        assertEquals(1, batch1.getBatchNumber());
        assertEquals(2, batch2.getBatchNumber());
        assertEquals(3, batch3.getBatchNumber());
    }

    @Test
    void hashExists_tracksWrittenHashes() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder().id(sourceId).name("Test").type(SourceType.DISK).build();
        outputService.initializeSource(source);

        List<FileDto> files = List.of(
            createTestFile(sourceId, "file1.txt", "hash123")
        );

        FileBatchDto batch = outputService.createBatch(sourceId, files);

        // When
        outputService.writeBatch(batch);

        // Then
        assertTrue(outputService.hashExists("hash123"));
        assertFalse(outputService.hashExists("nonexistent"));
    }

    @Test
    void checkHashes_returnsMapOfResults() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder().id(sourceId).name("Test").type(SourceType.DISK).build();
        outputService.initializeSource(source);

        List<FileDto> files = List.of(
            createTestFile(sourceId, "file1.txt", "hash1"),
            createTestFile(sourceId, "file2.txt", "hash2")
        );

        FileBatchDto batch = outputService.createBatch(sourceId, files);
        outputService.writeBatch(batch);

        // When
        var results = outputService.checkHashes(List.of("hash1", "hash2", "hash3"));

        // Then
        assertEquals(3, results.size());
        assertTrue(results.get("hash1"));
        assertTrue(results.get("hash2"));
        assertFalse(results.get("hash3"));
    }

    @Test
    void writeSummary_createsJsonFile() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder().id(sourceId).name("Test").type(SourceType.DISK).build();
        outputService.initializeSource(source);

        List<OutputService.ScanError> errors = List.of(
            new OutputService.ScanError("/path/to/file.txt", "Permission denied")
        );

        // When
        outputService.writeSummary(sourceId, 100L, 1000000L, errors, Instant.now());

        // Then
        Path summaryPath = tempDir.resolve(sourceId.toString()).resolve("summary.json");
        assertTrue(Files.exists(summaryPath));
    }

    @Test
    void reset_clearsBatchCounterAndHashes() throws IOException {
        // Given
        UUID sourceId = UUID.randomUUID();
        SourceDto source = SourceDto.builder().id(sourceId).name("Test").type(SourceType.DISK).build();
        outputService.initializeSource(source);

        List<FileDto> files = List.of(createTestFile(sourceId, "file.txt", "hash"));
        FileBatchDto batch = outputService.createBatch(sourceId, files);
        outputService.writeBatch(batch);

        // When
        outputService.reset();

        // Then
        assertEquals(0, outputService.getCurrentBatchNumber());
        assertFalse(outputService.hashExists("hash"));
    }

    @Test
    void getCurrentBatchNumber_returnsCorrectValue() {
        // Given
        UUID sourceId = UUID.randomUUID();
        List<FileDto> files = new ArrayList<>();

        // When
        outputService.createBatch(sourceId, files);
        outputService.createBatch(sourceId, files);

        // Then
        assertEquals(2, outputService.getCurrentBatchNumber());
    }

    private FileDto createTestFile(UUID sourceId, String name, String hash) {
        return FileDto.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .path("/test/" + name)
            .name(name)
            .extension("txt")
            .size(100L)
            .sha256(hash)
            .status(FileStatus.HASHED)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();
    }
}
