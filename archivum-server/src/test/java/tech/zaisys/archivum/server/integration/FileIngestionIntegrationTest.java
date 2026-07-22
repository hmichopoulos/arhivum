package tech.zaisys.archivum.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.*;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for file ingestion endpoint with real PostgreSQL database.
 * These tests verify that the schema can handle realistic data.
 *
 * TODO: Enable when Docker environment is properly configured for tests
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class FileIngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("archivum_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ScannedFileRepository fileRepository;

    private Source testSource;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
        sourceRepository.deleteAll();

        testSource = Source.builder()
            .name("Test Disk")
            .type(SourceType.DISK)
            .rootPath("/test")
            .status(ScanStatus.SCANNING)
            .sourceScanType(SourceScanType.DISCOVERY)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .postponed(false)
            .build();
        testSource = sourceRepository.save(testSource);
        sourceId = testSource.getId();
    }

    @Test
    void shouldIngestFileWithNormalExtension() throws Exception {
        // Given
        FileDto fileDto = createFileDto("document.pdf", "pdf", 50);

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(fileDto))
            .build();

        // When
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failureCount").value(0));

        // Then
        List<ScannedFile> files = fileRepository.findAll();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getExtension()).isEqualTo("pdf");
    }

    @Test
    void shouldIngestFileWithLongCompoundExtension() throws Exception {
        // Given - Test long extension (up to 100 chars)
        String longExtension = "component.spec.tsx.snapshot.backup";
        FileDto fileDto = createFileDto("test." + longExtension, longExtension, longExtension.length());

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(fileDto))
            .build();

        // When
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failureCount").value(0));

        // Then
        List<ScannedFile> files = fileRepository.findAll();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getExtension()).isEqualTo(longExtension);
    }

    @Test
    void shouldDefaultFileStateToDISCOVERED() throws Exception {
        // Test that files default to DISCOVERED state when ingested
        FileDto fileDto = createFileDto("file.txt", "txt", 10);

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(fileDto))
            .build();

        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1));

        List<ScannedFile> files = fileRepository.findAll();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getState()).isEqualTo(FileState.DISCOVERED);
    }

    @Test
    void shouldIngestFileWithAllZones() throws Exception {
        // Test that all Zone enum values can be persisted
        Zone[] zones = {Zone.UNKNOWN, Zone.MEDIA, Zone.DOCUMENTS, Zone.BOOKS,
                       Zone.SOFTWARE, Zone.BACKUP, Zone.CODE, Zone.SOFTWARE_LIB};

        for (int i = 0; i < zones.length; i++) {
            FileDto fileDto = createFileDto("file" + i + ".txt", "txt", 10);
            fileDto.setZone(zones[i]);

            FileBatchDto batch = FileBatchDto.builder()
                .sourceId(sourceId)
                .batchNumber(i + 1)
                .files(List.of(fileDto))
                .build();

            mockMvc.perform(post("/api/files/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.successCount").value(1));
        }

        List<ScannedFile> files = fileRepository.findAll();
        assertThat(files).hasSize(zones.length);
    }

    @Test
    void shouldIngestLargeBatch() throws Exception {
        // Given - 100 files in one batch
        List<FileDto> files = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(createFileDto("file" + i + ".txt", "txt", 10));
        }

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(files)
            .build();

        // When
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(100))
            .andExpect(jsonPath("$.failureCount").value(0));

        // Then
        List<ScannedFile> persistedFiles = fileRepository.findAll();
        assertThat(persistedFiles).hasSize(100);
    }

    @Test
    void shouldHandleLongMimeTypes() throws Exception {
        // Given - File with long MIME type (up to 100 chars)
        FileDto fileDto = createFileDto("test.bin", "bin", 10);
        fileDto.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(fileDto))
            .build();

        // When
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1));

        // Then
        List<ScannedFile> files = fileRepository.findAll();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getMimeType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void shouldPreserveDuplicateFlag() throws Exception {
        // Given
        FileDto fileDto = createFileDto("duplicate.txt", "txt", 10);
        fileDto.setIsDuplicate(true);

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(fileDto))
            .build();

        // When
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1));

        // Then
        List<ScannedFile> files = fileRepository.findAll();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getIsDuplicate()).isTrue();
    }

    private FileDto createFileDto(String filename, String extension, int extensionLength) {
        return FileDto.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .path("/test/" + filename)
            .name(filename)
            .extension(extension)
            .size(1024L)
            .sha256("a1b2c3d4e5f6" + "0".repeat(52))
            .modifiedAt(Instant.now())
            .createdAt(Instant.now())
            .accessedAt(Instant.now())
            .mimeType("application/octet-stream")
            .status(FileStatus.HASHED)
            .zone(Zone.UNKNOWN)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();
    }
}
