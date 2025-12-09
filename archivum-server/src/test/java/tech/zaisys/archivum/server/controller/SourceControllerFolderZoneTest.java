package tech.zaisys.archivum.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.service.FolderTreeService;
import tech.zaisys.archivum.server.service.FolderZoneService;
import tech.zaisys.archivum.server.service.SourceService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for SourceController folder zone endpoint.
 */
@WebMvcTest(SourceController.class)
@DisplayName("SourceController Folder Zone Tests")
class SourceControllerFolderZoneTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SourceService sourceService;

    @MockBean
    private FolderZoneService folderZoneService;

    @MockBean
    private FolderTreeService folderTreeService;

    private UUID sourceId;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should update folder zone successfully")
    void shouldUpdateFolderZoneSuccessfully() throws Exception {
        // Given
        String folderPath = "/path/to/folder";
        Map<String, String> request = new HashMap<>();
        request.put("folderPath", folderPath);
        request.put("zone", "MEDIA");

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(folderZoneService).setFolderZone(sourceId, folderPath, Zone.MEDIA);
        verify(folderTreeService).invalidateTree(sourceId);
    }

    @Test
    @DisplayName("should return bad request for invalid zone")
    void shouldReturnBadRequestForInvalidZone() throws Exception {
        // Given
        Map<String, String> request = new HashMap<>();
        request.put("folderPath", "/path/to/folder");
        request.put("zone", "INVALID_ZONE");

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verify(folderZoneService, never()).setFolderZone(any(), any(), any());
        verify(folderTreeService, never()).invalidateTree(any());
    }

    @Test
    @DisplayName("should return bad request for missing folder path")
    void shouldReturnBadRequestForMissingFolderPath() throws Exception {
        // Given
        Map<String, String> request = new HashMap<>();
        request.put("zone", "MEDIA");

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verify(folderZoneService, never()).setFolderZone(any(), any(), any());
    }

    @Test
    @DisplayName("should return bad request for missing zone")
    void shouldReturnBadRequestForMissingZone() throws Exception {
        // Given
        Map<String, String> request = new HashMap<>();
        request.put("folderPath", "/path/to/folder");

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verify(folderZoneService, never()).setFolderZone(any(), any(), any());
    }

    @Test
    @DisplayName("should update folder zone for all valid zones")
    void shouldUpdateFolderZoneForAllValidZones() throws Exception {
        // Test all valid zones
        for (Zone zone : Zone.values()) {
            Map<String, String> request = new HashMap<>();
            request.put("folderPath", "/path/to/folder");
            request.put("zone", zone.name());

            mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            verify(folderZoneService).setFolderZone(sourceId, "/path/to/folder", zone);
        }

        // Should invalidate tree for each zone update
        verify(folderTreeService, times(Zone.values().length)).invalidateTree(sourceId);
    }

    @Test
    @DisplayName("should handle service exception gracefully")
    void shouldHandleServiceExceptionGracefully() throws Exception {
        // Given
        String folderPath = "/path/to/folder";
        Map<String, String> request = new HashMap<>();
        request.put("folderPath", folderPath);
        request.put("zone", "MEDIA");

        doThrow(new IllegalArgumentException("Source not found"))
            .when(folderZoneService).setFolderZone(sourceId, folderPath, Zone.MEDIA);

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Source not found"));

        verify(folderTreeService, never()).invalidateTree(any());
    }

    @Test
    @DisplayName("should handle deeply nested folder paths")
    void shouldHandleDeeplyNestedFolderPaths() throws Exception {
        // Given
        String deepPath = "/very/deep/folder/structure/with/many/levels/here";
        Map<String, String> request = new HashMap<>();
        request.put("folderPath", deepPath);
        request.put("zone", "CODE");

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(folderZoneService).setFolderZone(sourceId, deepPath, Zone.CODE);
        verify(folderTreeService).invalidateTree(sourceId);
    }

    @Test
    @DisplayName("should handle paths with special characters")
    void shouldHandlePathsWithSpecialCharacters() throws Exception {
        // Given
        String specialPath = "/path/with spaces/and-dashes/and_underscores";
        Map<String, String> request = new HashMap<>();
        request.put("folderPath", specialPath);
        request.put("zone", "DOCUMENTS");

        // When/Then
        mockMvc.perform(patch("/api/sources/{id}/folders/zone", sourceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(folderZoneService).setFolderZone(sourceId, specialPath, Zone.DOCUMENTS);
        verify(folderTreeService).invalidateTree(sourceId);
    }
}
