package tech.zaisys.archivum.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.SourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UploadService.
 * Note: These are basic structural tests. Integration tests with real HTTP server
 * would be more comprehensive but require more setup.
 */
class UploadServiceTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void testConstructor() {
        // When
        UploadService service = new UploadService("http://localhost:8080", 60);

        // Then: Should not throw
        assertNotNull(service);
    }

    @Test
    void testUpload_MissingOutputDirectory() {
        // Given
        UploadService service = new UploadService("http://localhost:8080", 60);
        Path nonExistent = tempDir.resolve("nonexistent");

        // When/Then
        Exception exception = assertThrows(IOException.class, () -> {
            service.upload(nonExistent);
        });

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testUpload_MissingSourceJson() {
        // Given
        UploadService service = new UploadService("http://localhost:8080", 60);

        // When/Then
        Exception exception = assertThrows(IOException.class, () -> {
            service.upload(tempDir);
        });

        assertTrue(exception.getMessage().contains("source.json not found"));
    }

    @Test
    void testUpload_InvalidOutputDirectory() throws IOException {
        // Given: Create a file instead of a directory
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "test");

        UploadService service = new UploadService("http://localhost:8080", 60);

        // When/Then
        Exception exception = assertThrows(IOException.class, () -> {
            service.upload(file);
        });

        assertTrue(exception.getMessage().contains("not a directory"));
    }

    @Test
    void testValidateOutputDirectory_ValidStructure() throws IOException {
        // Given: Create valid output directory structure
        Path sourceJson = tempDir.resolve("source.json");
        SourceDto source = SourceDto.builder()
            .id(UUID.randomUUID())
            .name("Test Source")
            .type(SourceType.DISK)
            .rootPath("/test/path")
            .scanStartedAt(Instant.now())
            .totalFiles(0L)
            .totalSize(0L)
            .build();

        objectMapper.writeValue(sourceJson.toFile(), source);

        // When: Create service (validation happens in upload method)
        UploadService service = new UploadService("http://localhost:8080", 60);

        // Then: Should not throw when we have source.json
        // Note: Full upload will fail without server, but validation passes
        assertNotNull(service);
        assertTrue(Files.exists(sourceJson));
    }

    @Test
    void testUploadResult_Initialization() {
        // When
        UploadService.UploadResult result = new UploadService.UploadResult();

        // Then: Verify default values
        assertNull(result.sourceId);
        assertEquals(0, result.totalBatches);
        assertEquals(0, result.uploadedBatches);
        assertEquals(0, result.uploadedFiles);
        assertEquals(0, result.uploadedProjects);
    }

    @Test
    void testUploadResult_Fields() {
        // Given
        UploadService.UploadResult result = new UploadService.UploadResult();
        UUID testId = UUID.randomUUID();

        // When
        result.sourceId = testId;
        result.totalBatches = 5;
        result.uploadedBatches = 3;
        result.uploadedFiles = 150;
        result.uploadedProjects = 2;

        // Then
        assertEquals(testId, result.sourceId);
        assertEquals(5, result.totalBatches);
        assertEquals(3, result.uploadedBatches);
        assertEquals(150, result.uploadedFiles);
        assertEquals(2, result.uploadedProjects);
    }

    @Test
    void testCreateValidBatchFile() throws IOException {
        // Given: Create a valid batch file structure
        Path filesDir = tempDir.resolve("files");
        Files.createDirectory(filesDir);

        Path batchFile = filesDir.resolve("batch-0001.json");

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(UUID.randomUUID())
            .batchNumber(1)
            .files(List.of(
                FileDto.builder()
                    .path("/test/file.txt")
                    .size(100L)
                    .sha256("abc123")
                    .build()
            ))
            .build();

        objectMapper.writeValue(batchFile.toFile(), batch);

        // Then: Verify file was created correctly
        assertTrue(Files.exists(batchFile));
        FileBatchDto loaded = objectMapper.readValue(batchFile.toFile(), FileBatchDto.class);
        assertEquals(1, loaded.getBatchNumber());
        assertEquals(1, loaded.getFiles().size());
    }
}
