package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.server.domain.CodeProject;
import tech.zaisys.archivum.server.repository.CodeProjectRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing code projects.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CodeProjectService {

    private final CodeProjectRepository repository;

    /**
     * Save a code project from DTO.
     */
    @Transactional
    public CodeProjectDto save(CodeProjectDto dto) {
        CodeProject entity = toEntity(dto);
        CodeProject saved = repository.save(entity);
        log.info("Saved code project: {} ({})", saved.getIdentifier(), saved.getId());
        return toDto(saved);
    }

    /**
     * Save multiple code projects.
     */
    @Transactional
    public List<CodeProjectDto> saveAll(List<CodeProjectDto> dtos) {
        List<CodeProject> entities = dtos.stream()
            .map(this::toEntity)
            .collect(Collectors.toList());

        List<CodeProject> saved = repository.saveAll(entities);
        log.info("Saved {} code projects", saved.size());

        return saved.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find project by ID.
     */
    public Optional<CodeProjectDto> findById(UUID id) {
        return repository.findById(id).map(this::toDto);
    }

    /**
     * Find all projects.
     */
    public List<CodeProjectDto> findAll() {
        return repository.findAll().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find all projects ordered by scan date (most recent first).
     */
    public List<CodeProjectDto> findAllByRecentFirst() {
        return repository.findAllByOrderByScannedAtDesc().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find projects by source ID.
     */
    public List<CodeProjectDto> findBySourceId(UUID sourceId) {
        return repository.findBySourceId(sourceId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find projects by type.
     */
    public List<CodeProjectDto> findByType(ProjectType type) {
        return repository.findByProjectType(type).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find projects by identifier.
     */
    public List<CodeProjectDto> findByIdentifier(String identifier) {
        return repository.findByIdentifier(identifier).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Find exact duplicates (same content hash).
     */
    public List<CodeProjectDto> findExactDuplicates(String contentHash) {
        return repository.findByContentHash(contentHash).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Delete project by ID.
     */
    @Transactional
    public void deleteById(UUID id) {
        repository.deleteById(id);
        log.info("Deleted code project: {}", id);
    }

    /**
     * Convert DTO to entity.
     */
    private CodeProject toEntity(CodeProjectDto dto) {
        ProjectIdentityDto identity = dto.getIdentity();

        return CodeProject.builder()
            .id(dto.getId())
            .sourceId(dto.getSourceId())
            .rootPath(dto.getRootPath())
            .projectType(identity.getType())
            .name(identity.getName())
            .version(identity.getVersion())
            .groupId(identity.getGroupId())
            .gitRemote(identity.getGitRemote())
            .gitBranch(identity.getGitBranch())
            .gitCommit(identity.getGitCommit())
            .identifier(identity.getIdentifier())
            .contentHash(dto.getContentHash())
            .sourceFileCount(dto.getSourceFileCount())
            .totalFileCount(dto.getTotalFileCount())
            .totalSizeBytes(dto.getTotalSizeBytes())
            .scannedAt(dto.getScannedAt())
            .archivePath(dto.getArchivePath())
            .build();
    }

    /**
     * Convert entity to DTO.
     */
    private CodeProjectDto toDto(CodeProject entity) {
        ProjectIdentityDto identity = ProjectIdentityDto.builder()
            .type(entity.getProjectType())
            .name(entity.getName())
            .version(entity.getVersion())
            .groupId(entity.getGroupId())
            .gitRemote(entity.getGitRemote())
            .gitBranch(entity.getGitBranch())
            .gitCommit(entity.getGitCommit())
            .identifier(entity.getIdentifier())
            .build();

        return CodeProjectDto.builder()
            .id(entity.getId())
            .sourceId(entity.getSourceId())
            .rootPath(entity.getRootPath())
            .identity(identity)
            .contentHash(entity.getContentHash())
            .sourceFileCount(entity.getSourceFileCount())
            .totalFileCount(entity.getTotalFileCount())
            .totalSizeBytes(entity.getTotalSizeBytes())
            .scannedAt(entity.getScannedAt())
            .archivePath(entity.getArchivePath())
            .build();
    }
}
