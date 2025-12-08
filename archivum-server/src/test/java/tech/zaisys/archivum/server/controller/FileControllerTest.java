package tech.zaisys.archivum.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.enums.FileStatus;
import tech.zaisys.archivum.server.service.FileBatchResult;
import tech.zaisys.archivum.server.service.FileService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for FileController using MockMvc.
 */
@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileService fileService;

    private UUID sourceId;
    private UUID fileId;
    private FileDto testFileDto;
    private FileBatchDto testBatch;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        fileId = UUID.randomUUID();

        testFileDto = FileDto.builder()
            .id(fileId)
            .sourceId(sourceId)
            .path("photos/vacation.jpg")
            .name("vacation.jpg")
            .extension("jpg")
            .size(1024000L)
            .sha256("a1b2c3d4e5f6" + "0".repeat(52))
            .modifiedAt(Instant.now())
            .createdAt(Instant.now())
            .mimeType("image/jpeg")
            .status(FileStatus.HASHED)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();

        testBatch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(testFileDto))
            .build();
    }

    @Test
    void testIngestBatch_Success() throws Exception {
        // Given
        FileBatchResult result = new FileBatchResult();
        result.setBatchNumber(1);
        result.setTotalFiles(1);
        result.setSuccessCount(1);
        result.setFailureCount(0);
        result.setSuccessfulFileIds(List.of(fileId));

        when(fileService.ingestBatch(any(FileBatchDto.class))).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testBatch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.batchNumber").value(1))
            .andExpect(jsonPath("$.totalFiles").value(1))
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failureCount").value(0))
            .andExpect(jsonPath("$.successfulFileIds.length()").value(1));
    }

    @Test
    void testIngestBatch_PartialFailure() throws Exception {
        // Given
        FileBatchResult result = new FileBatchResult();
        result.setBatchNumber(1);
        result.setTotalFiles(2);
        result.setSuccessCount(1);
        result.setFailureCount(1);
        result.setSuccessfulFileIds(List.of(fileId));
        result.setErrors(List.of(
            new FileBatchResult.FileError("invalid/file.jpg", "Database error")
        ));

        when(fileService.ingestBatch(any(FileBatchDto.class))).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testBatch)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.failureCount").value(1))
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].path").value("invalid/file.jpg"));
    }

    @Test
    void testIngestBatch_SourceNotFound() throws Exception {
        // Given
        when(fileService.ingestBatch(any(FileBatchDto.class)))
            .thenThrow(new IllegalArgumentException("Source not found"));

        // When/Then
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testBatch)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testIngestBatch_PreservesDuplicateFlag() throws Exception {
        // Given - File marked as duplicate by scanner
        testFileDto.setIsDuplicate(true);
        FileBatchDto batchWithDuplicate = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(testFileDto))
            .build();

        FileBatchResult result = new FileBatchResult();
        result.setBatchNumber(1);
        result.setTotalFiles(1);
        result.setSuccessCount(1);
        result.setSuccessfulFileIds(List.of(fileId));

        when(fileService.ingestBatch(any(FileBatchDto.class))).thenReturn(result);

        // When/Then
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchWithDuplicate)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void testGetFileById_Found() throws Exception {
        // Given
        when(fileService.findById(fileId)).thenReturn(Optional.of(testFileDto));

        // When/Then
        mockMvc.perform(get("/api/files/{id}", fileId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fileId.toString()))
            .andExpect(jsonPath("$.path").value("photos/vacation.jpg"))
            .andExpect(jsonPath("$.name").value("vacation.jpg"))
            .andExpect(jsonPath("$.extension").value("jpg"))
            .andExpect(jsonPath("$.size").value(1024000))
            .andExpect(jsonPath("$.isDuplicate").value(false));
    }

    @Test
    void testGetFileById_NotFound() throws Exception {
        // Given
        when(fileService.findById(fileId)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/files/{id}", fileId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testIngestBatch_InvalidJson() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/files/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
            .andExpect(status().isBadRequest());
    }
}
