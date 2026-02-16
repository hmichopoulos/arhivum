package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.server.api.dto.DestinationDto;
import tech.zaisys.archivum.server.domain.Destination;
import tech.zaisys.archivum.server.repository.DestinationRepository;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing migration destinations with real-time disk space monitoring.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;

    /**
     * Get all destinations with real-time disk space information.
     */
    @Transactional(readOnly = true)
    public List<DestinationDto> getAllDestinations() {
        return destinationRepository.findAll().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Get destination by ID with real-time disk space information.
     */
    @Transactional(readOnly = true)
    public Optional<DestinationDto> getDestinationById(UUID id) {
        return destinationRepository.findById(id)
            .map(this::toDto);
    }

    /**
     * Get all active destinations ordered by priority.
     */
    @Transactional(readOnly = true)
    public List<DestinationDto> getActiveDestinations() {
        return destinationRepository.findByIsActiveTrueOrderByPriorityDesc().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Create a new destination.
     */
    @Transactional
    public DestinationDto createDestination(DestinationDto dto) {
        // Validate mounted path doesn't already exist
        if (destinationRepository.existsByMountedPath(dto.getMountedPath())) {
            throw new IllegalArgumentException("Destination with mounted path already exists: " + dto.getMountedPath());
        }

        // Validate path exists and is accessible
        Path path = Paths.get(dto.getMountedPath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Mounted path does not exist: " + dto.getMountedPath());
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Mounted path is not a directory: " + dto.getMountedPath());
        }
        if (!Files.isWritable(path)) {
            throw new IllegalArgumentException("Mounted path is not writable: " + dto.getMountedPath());
        }

        Destination destination = Destination.builder()
            .name(dto.getName())
            .destinationType(dto.getDestinationType())
            .mountedPath(dto.getMountedPath())
            .physicalIdentifier(dto.getPhysicalIdentifier())
            .description(dto.getDescription())
            .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
            .totalCapacityBytes(dto.getTotalCapacityBytes())
            .priority(dto.getPriority() != null ? dto.getPriority() : 0)
            .build();

        destination = destinationRepository.save(destination);
        log.info("Created destination: {} at {}", destination.getName(), destination.getMountedPath());

        return toDto(destination);
    }

    /**
     * Update an existing destination.
     */
    @Transactional
    public DestinationDto updateDestination(UUID id, DestinationDto dto) {
        Destination destination = destinationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Destination not found: " + id));

        // If mounted path is being changed, validate it
        if (dto.getMountedPath() != null && !dto.getMountedPath().equals(destination.getMountedPath())) {
            if (destinationRepository.existsByMountedPath(dto.getMountedPath())) {
                throw new IllegalArgumentException("Destination with mounted path already exists: " + dto.getMountedPath());
            }
            Path path = Paths.get(dto.getMountedPath());
            if (!Files.exists(path) || !Files.isDirectory(path) || !Files.isWritable(path)) {
                throw new IllegalArgumentException("Invalid mounted path: " + dto.getMountedPath());
            }
            destination.setMountedPath(dto.getMountedPath());
        }

        if (dto.getName() != null) {
            destination.setName(dto.getName());
        }
        if (dto.getDestinationType() != null) {
            destination.setDestinationType(dto.getDestinationType());
        }
        if (dto.getPhysicalIdentifier() != null) {
            destination.setPhysicalIdentifier(dto.getPhysicalIdentifier());
        }
        if (dto.getDescription() != null) {
            destination.setDescription(dto.getDescription());
        }
        if (dto.getIsActive() != null) {
            destination.setIsActive(dto.getIsActive());
        }
        if (dto.getTotalCapacityBytes() != null) {
            destination.setTotalCapacityBytes(dto.getTotalCapacityBytes());
        }
        if (dto.getPriority() != null) {
            destination.setPriority(dto.getPriority());
        }

        destination = destinationRepository.save(destination);
        log.info("Updated destination: {}", destination.getId());

        return toDto(destination);
    }

    /**
     * Delete a destination.
     */
    @Transactional
    public void deleteDestination(UUID id) {
        if (!destinationRepository.existsById(id)) {
            throw new IllegalArgumentException("Destination not found: " + id);
        }
        destinationRepository.deleteById(id);
        log.info("Deleted destination: {}", id);
    }

    /**
     * Convert entity to DTO with real-time disk space information.
     */
    private DestinationDto toDto(Destination destination) {
        DestinationDto dto = DestinationDto.builder()
            .id(destination.getId())
            .name(destination.getName())
            .destinationType(destination.getDestinationType())
            .mountedPath(destination.getMountedPath())
            .physicalIdentifier(destination.getPhysicalIdentifier())
            .description(destination.getDescription())
            .isActive(destination.getIsActive())
            .totalCapacityBytes(destination.getTotalCapacityBytes())
            .priority(destination.getPriority())
            .createdAt(destination.getCreatedAt())
            .updatedAt(destination.getUpdatedAt())
            .build();

        // Add real-time filesystem information
        enrichWithFilesystemInfo(dto, destination.getMountedPath());

        return dto;
    }

    /**
     * Enrich DTO with real-time filesystem information.
     */
    private void enrichWithFilesystemInfo(DestinationDto dto, String mountedPath) {
        try {
            Path path = Paths.get(mountedPath);

            if (!Files.exists(path)) {
                dto.setIsAccessible(false);
                dto.setErrorMessage("Path does not exist");
                return;
            }

            FileStore store = Files.getFileStore(path);

            long totalSpace = store.getTotalSpace();
            long usableSpace = store.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            dto.setIsAccessible(true);
            dto.setAvailableSpaceBytes(usableSpace);
            dto.setUsedSpaceBytes(usedSpace);

            // If total capacity is manually set, use it; otherwise use filesystem value
            long capacity = dto.getTotalCapacityBytes() != null ? dto.getTotalCapacityBytes() : totalSpace;
            if (capacity > 0) {
                dto.setUsagePercentage((double) usedSpace / capacity * 100.0);
            }

            // Update total capacity if not manually set
            if (dto.getTotalCapacityBytes() == null) {
                dto.setTotalCapacityBytes(totalSpace);
            }

        } catch (IOException e) {
            log.warn("Failed to read filesystem info for {}: {}", mountedPath, e.getMessage());
            dto.setIsAccessible(false);
            dto.setErrorMessage("Failed to access filesystem: " + e.getMessage());
        }
    }
}
