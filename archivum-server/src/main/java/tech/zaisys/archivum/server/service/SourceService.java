package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.dto.CreateSourceRequest;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing sources and their hierarchical relationships.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;

    /**
     * Create a new source from a request.
     *
     * @param request Source creation request
     * @return Created source DTO
     */
    @Transactional
    public SourceDto createSource(CreateSourceRequest request) {
        Source source = Source.builder()
            .id(request.getId())
            .name(request.getName())
            .type(request.getType())
            .rootPath(request.getRootPath())
            .physicalId(request.getPhysicalId())
            .status(tech.zaisys.archivum.api.enums.ScanStatus.PENDING)
            .postponed(request.getPostponed() != null ? request.getPostponed() : false)
            .notes(request.getNotes())
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .build();

        // Set parent if specified
        if (request.getParentSourceId() != null) {
            Source parent = sourceRepository.findById(request.getParentSourceId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Parent source not found: " + request.getParentSourceId()));
            parent.addChild(source);
        }

        Source saved = sourceRepository.save(source);
        log.info("Created source: {} (type: {}, parent: {})",
            saved.getName(), saved.getType(),
            saved.getParent() != null ? saved.getParent().getId() : "none");

        return toDto(saved);
    }

    /**
     * Find source by ID.
     *
     * @param id Source ID
     * @return Optional containing source DTO if found
     */
    @Transactional(readOnly = true)
    public Optional<SourceDto> findById(UUID id) {
        return sourceRepository.findById(id).map(this::toDto);
    }

    /**
     * Find all sources.
     *
     * @return List of all source DTOs
     */
    @Transactional(readOnly = true)
    public List<SourceDto> findAll() {
        return sourceRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find all root sources (no parent).
     *
     * @return List of root source DTOs
     */
    @Transactional(readOnly = true)
    public List<SourceDto> findRootSources() {
        return sourceRepository.findByParentIsNull()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find all child sources of a parent.
     *
     * @param parentId Parent source ID
     * @return List of child source DTOs
     */
    @Transactional(readOnly = true)
    public List<SourceDto> findChildren(UUID parentId) {
        return sourceRepository.findByParentId(parentId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Link a child source to a parent source.
     *
     * @param parentId Parent source ID
     * @param childId Child source ID
     */
    @Transactional
    public void linkParentChild(UUID parentId, UUID childId) {
        Source parent = sourceRepository.findById(parentId)
            .orElseThrow(() -> new IllegalArgumentException("Parent source not found: " + parentId));
        Source child = sourceRepository.findById(childId)
            .orElseThrow(() -> new IllegalArgumentException("Child source not found: " + childId));

        parent.addChild(child);
        sourceRepository.save(parent);

        log.info("Linked child source {} to parent {}", childId, parentId);
    }

    /**
     * Delete a source and all its children (cascade).
     *
     * @param id Source ID to delete
     */
    @Transactional
    public void deleteSource(UUID id) {
        Source source = sourceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));

        log.info("Deleting source: {} (will cascade to {} children)",
            source.getName(), source.getChildren().size());

        sourceRepository.delete(source);
    }

    /**
     * Convert Source entity to SourceDto.
     *
     * @param source Source entity
     * @return SourceDto
     */
    private SourceDto toDto(Source source) {
        return SourceDto.builder()
            .id(source.getId())
            .name(source.getName())
            .type(source.getType())
            .rootPath(source.getRootPath())
            .physicalId(source.getPhysicalId())
            .parentSourceId(source.getParent() != null ? source.getParent().getId() : null)
            .childSourceIds(source.getChildren().stream()
                .map(Source::getId)
                .collect(Collectors.toList()))
            .status(source.getStatus())
            .postponed(source.getPostponed())
            .totalFiles(source.getTotalFiles())
            .totalSize(source.getTotalSize())
            .processedFiles(source.getProcessedFiles())
            .processedSize(source.getProcessedSize())
            .scanStartedAt(source.getScanStartedAt())
            .scanCompletedAt(source.getScanCompletedAt())
            .createdAt(source.getCreatedAt())
            .notes(source.getNotes())
            .build();
    }
}
