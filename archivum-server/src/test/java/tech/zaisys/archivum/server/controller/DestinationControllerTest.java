package tech.zaisys.archivum.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.zaisys.archivum.server.api.dto.DestinationDto;
import tech.zaisys.archivum.server.domain.Destination;
import tech.zaisys.archivum.server.service.DestinationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for DestinationController using MockMvc.
 */
@WebMvcTest(DestinationController.class)
class DestinationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DestinationService destinationService;

    private UUID destinationId;
    private DestinationDto testDestination;

    @BeforeEach
    void setUp() {
        destinationId = UUID.randomUUID();
        testDestination = DestinationDto.builder()
            .id(destinationId)
            .name("Test NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath("/mnt/nas/archive")
            .physicalIdentifier("nas-001")
            .description("Test destination")
            .isActive(true)
            .totalCapacityBytes(1000000000000L)
            .availableSpaceBytes(500000000000L)
            .usedSpaceBytes(500000000000L)
            .usagePercentage(50.0)
            .priority(10)
            .isAccessible(true)
            .build();
    }

    @Test
    void testGetAllDestinations() throws Exception {
        // Given
        when(destinationService.getAllDestinations()).thenReturn(List.of(testDestination));

        // When/Then
        mockMvc.perform(get("/api/destinations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(destinationId.toString()))
            .andExpect(jsonPath("$[0].name").value("Test NAS"))
            .andExpect(jsonPath("$[0].destinationType").value("NAS"))
            .andExpect(jsonPath("$[0].mountedPath").value("/mnt/nas/archive"))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[0].isAccessible").value(true))
            .andExpect(jsonPath("$[0].availableSpaceBytes").value(500000000000L));
    }

    @Test
    void testGetDestinationById_Found() throws Exception {
        // Given
        when(destinationService.getDestinationById(destinationId)).thenReturn(Optional.of(testDestination));

        // When/Then
        mockMvc.perform(get("/api/destinations/{id}", destinationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(destinationId.toString()))
            .andExpect(jsonPath("$.name").value("Test NAS"));
    }

    @Test
    void testGetDestinationById_NotFound() throws Exception {
        // Given
        when(destinationService.getDestinationById(any())).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/destinations/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetActiveDestinations() throws Exception {
        // Given
        when(destinationService.getActiveDestinations()).thenReturn(List.of(testDestination));

        // When/Then
        mockMvc.perform(get("/api/destinations/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void testCreateDestination_Success() throws Exception {
        // Given
        DestinationDto newDestination = DestinationDto.builder()
            .name("New NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath("/mnt/nas/new")
            .physicalIdentifier("nas-002")
            .isActive(true)
            .priority(5)
            .build();

        when(destinationService.createDestination(any(DestinationDto.class))).thenReturn(testDestination);

        // When/Then
        mockMvc.perform(post("/api/destinations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newDestination)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test NAS"));
    }

    @Test
    void testCreateDestination_InvalidPath() throws Exception {
        // Given
        DestinationDto invalidDestination = DestinationDto.builder()
            .name("Invalid")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath("/nonexistent")
            .build();

        when(destinationService.createDestination(any())).thenThrow(new IllegalArgumentException("Path does not exist"));

        // When/Then
        mockMvc.perform(post("/api/destinations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDestination)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateDestination_Success() throws Exception {
        // Given
        DestinationDto updateDto = DestinationDto.builder()
            .name("Updated NAS")
            .priority(20)
            .build();

        DestinationDto updated = DestinationDto.builder()
            .id(destinationId)
            .name("Updated NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath("/mnt/nas/archive")
            .priority(20)
            .isActive(true)
            .build();

        when(destinationService.updateDestination(eq(destinationId), any(DestinationDto.class))).thenReturn(updated);

        // When/Then
        mockMvc.perform(put("/api/destinations/{id}", destinationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated NAS"))
            .andExpect(jsonPath("$.priority").value(20));
    }

    @Test
    void testDeleteDestination_Success() throws Exception {
        // When/Then
        mockMvc.perform(delete("/api/destinations/{id}", destinationId))
            .andExpect(status().isOk());
    }

    @Test
    void testDeleteDestination_NotFound() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("not found"))
            .when(destinationService).deleteDestination(any());

        // When/Then
        mockMvc.perform(delete("/api/destinations/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }
}
