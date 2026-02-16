package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.server.api.dto.DestinationDto;
import tech.zaisys.archivum.server.domain.Destination;
import tech.zaisys.archivum.server.repository.DestinationRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DestinationService.
 */
@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock
    private DestinationRepository destinationRepository;

    @InjectMocks
    private DestinationService destinationService;

    @TempDir
    Path tempDir;

    private Destination testDestination;
    private UUID destinationId;

    @BeforeEach
    void setUp() {
        destinationId = UUID.randomUUID();
        testDestination = Destination.builder()
            .id(destinationId)
            .name("Test NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath(tempDir.toString())
            .physicalIdentifier("nas-001")
            .description("Test destination")
            .isActive(true)
            .totalCapacityBytes(1000000000L)
            .priority(10)
            .build();
    }

    @Test
    void shouldGetAllDestinations() {
        // Given
        when(destinationRepository.findAll()).thenReturn(List.of(testDestination));

        // When
        List<DestinationDto> result = destinationService.getAllDestinations();

        // Then
        assertThat(result).hasSize(1);
        DestinationDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(destinationId);
        assertThat(dto.getName()).isEqualTo("Test NAS");
        assertThat(dto.getDestinationType()).isEqualTo(Destination.DestinationType.NAS);
        assertThat(dto.getMountedPath()).isEqualTo(tempDir.toString());
        assertThat(dto.getIsAccessible()).isTrue();
        assertThat(dto.getAvailableSpaceBytes()).isNotNull();
    }

    @Test
    void shouldGetDestinationById() {
        // Given
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(testDestination));

        // When
        Optional<DestinationDto> result = destinationService.getDestinationById(destinationId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(destinationId);
        assertThat(result.get().getName()).isEqualTo("Test NAS");
    }

    @Test
    void shouldGetActiveDestinations() {
        // Given
        when(destinationRepository.findByIsActiveTrueOrderByPriorityDesc())
            .thenReturn(List.of(testDestination));

        // When
        List<DestinationDto> result = destinationService.getActiveDestinations();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
    }

    @Test
    void shouldCreateDestination() throws Exception {
        // Given
        Path newPath = tempDir.resolve("nas");
        Files.createDirectory(newPath);

        DestinationDto dto = DestinationDto.builder()
            .name("New NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath(newPath.toString())
            .physicalIdentifier("nas-002")
            .description("New destination")
            .isActive(true)
            .priority(5)
            .build();

        Destination saved = Destination.builder()
            .id(UUID.randomUUID())
            .name(dto.getName())
            .destinationType(dto.getDestinationType())
            .mountedPath(dto.getMountedPath())
            .physicalIdentifier(dto.getPhysicalIdentifier())
            .description(dto.getDescription())
            .isActive(dto.getIsActive())
            .priority(dto.getPriority())
            .build();

        when(destinationRepository.existsByMountedPath(anyString())).thenReturn(false);
        when(destinationRepository.save(any(Destination.class))).thenReturn(saved);

        // When
        DestinationDto result = destinationService.createDestination(dto);

        // Then
        assertThat(result.getName()).isEqualTo("New NAS");
        assertThat(result.getMountedPath()).isEqualTo(newPath.toString());
        verify(destinationRepository).save(any(Destination.class));
    }

    @Test
    void shouldFailToCreateDestinationWithDuplicatePath() throws Exception {
        // Given
        Path newPath = tempDir.resolve("nas");
        Files.createDirectory(newPath);

        DestinationDto dto = DestinationDto.builder()
            .name("New NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath(newPath.toString())
            .build();

        when(destinationRepository.existsByMountedPath(newPath.toString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> destinationService.createDestination(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        verify(destinationRepository, never()).save(any());
    }

    @Test
    void shouldFailToCreateDestinationWithNonExistentPath() {
        // Given
        DestinationDto dto = DestinationDto.builder()
            .name("New NAS")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath("/nonexistent/path")
            .build();

        when(destinationRepository.existsByMountedPath(anyString())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> destinationService.createDestination(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist");

        verify(destinationRepository, never()).save(any());
    }

    @Test
    void shouldUpdateDestination() {
        // Given
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(testDestination));
        when(destinationRepository.save(any(Destination.class))).thenReturn(testDestination);

        DestinationDto updateDto = DestinationDto.builder()
            .name("Updated NAS")
            .priority(20)
            .build();

        // When
        DestinationDto result = destinationService.updateDestination(destinationId, updateDto);

        // Then
        verify(destinationRepository).save(any(Destination.class));
        assertThat(testDestination.getName()).isEqualTo("Updated NAS");
        assertThat(testDestination.getPriority()).isEqualTo(20);
    }

    @Test
    void shouldDeleteDestination() {
        // Given
        when(destinationRepository.existsById(destinationId)).thenReturn(true);

        // When
        destinationService.deleteDestination(destinationId);

        // Then
        verify(destinationRepository).deleteById(destinationId);
    }

    @Test
    void shouldFailToDeleteNonExistentDestination() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(destinationRepository.existsById(nonExistentId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> destinationService.deleteDestination(nonExistentId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");

        verify(destinationRepository, never()).deleteById(any());
    }

    @Test
    void shouldEnrichDtoWithRealTimeFilesystemInfo() {
        // Given
        when(destinationRepository.findById(destinationId)).thenReturn(Optional.of(testDestination));

        // When
        Optional<DestinationDto> result = destinationService.getDestinationById(destinationId);

        // Then
        assertThat(result).isPresent();
        DestinationDto dto = result.get();

        // Real-time filesystem info should be populated
        assertThat(dto.getIsAccessible()).isTrue();
        assertThat(dto.getAvailableSpaceBytes()).isNotNull();
        assertThat(dto.getUsedSpaceBytes()).isNotNull();
        assertThat(dto.getUsagePercentage()).isNotNull();
        assertThat(dto.getErrorMessage()).isNull();
    }

    @Test
    void shouldHandleInaccessiblePath() {
        // Given
        Destination inaccessible = Destination.builder()
            .id(UUID.randomUUID())
            .name("Inaccessible")
            .destinationType(Destination.DestinationType.NAS)
            .mountedPath("/nonexistent")
            .isActive(true)
            .build();

        when(destinationRepository.findById(any())).thenReturn(Optional.of(inaccessible));

        // When
        Optional<DestinationDto> result = destinationService.getDestinationById(inaccessible.getId());

        // Then
        assertThat(result).isPresent();
        DestinationDto dto = result.get();
        assertThat(dto.getIsAccessible()).isFalse();
        assertThat(dto.getErrorMessage()).isNotNull();
    }
}
