package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.domain.CodeProject;
import tech.zaisys.archivum.server.repository.CodeProjectRepository;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final FolderZoneService folderZoneService;
    private final ScannedFileRepository scannedFileRepository;

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
     * Find all projects ordered by scan date (most recent first),
     * excluding projects whose folders have been reclassified to non-CODE zones.
     *
     * A project is included if:
     * - Its folder has explicit zone CODE, OR
     * - Its folder inherits zone CODE from parent, OR
     * - Its folder has no explicit zone set (default behavior)
     *
     * A project is excluded if:
     * - Its folder has explicit zone set to something other than CODE
     */
    public List<CodeProjectDto> findAllByRecentFirstExcludingNonCodeZones() {
        List<CodeProject> allProjects = repository.findAllByOrderByScannedAtDesc();

        // Group projects by source ID for efficient zone lookup
        Map<UUID, List<CodeProject>> projectsBySource = allProjects.stream()
            .collect(Collectors.groupingBy(CodeProject::getSourceId));

        List<CodeProjectDto> result = new java.util.ArrayList<>();

        // For each source, load zones and filter projects
        for (Map.Entry<UUID, List<CodeProject>> entry : projectsBySource.entrySet()) {
            UUID sourceId = entry.getKey();
            List<CodeProject> sourceProjects = entry.getValue();

            // Load folder zones for this source
            Map<String, Zone> folderZoneMap = folderZoneService.loadFolderZones(sourceId);

            // Filter projects based on zone
            for (CodeProject project : sourceProjects) {
                if (shouldIncludeProject(sourceId, project.getRootPath(), folderZoneMap)) {
                    result.add(toDto(project));
                }
            }
        }

        log.debug("Filtered {} projects down to {} CODE zone projects",
            allProjects.size(), result.size());

        return result;
    }

    /**
     * Determine if a code project should be included based on its zone classification.
     *
     * @param sourceId Source ID
     * @param rootPath Project root path
     * @param folderZoneMap Preloaded folder zones for the source
     * @return true if project should be included, false otherwise
     */
    private boolean shouldIncludeProject(UUID sourceId, String rootPath, Map<String, Zone> folderZoneMap) {
        FolderZoneService.ZoneResult zoneResult = folderZoneService.getZoneForFolder(
            sourceId, rootPath, folderZoneMap);

        // If no zone set (null), include it (default behavior)
        if (zoneResult == null) {
            return true;
        }

        // Only include if zone is CODE
        return zoneResult.zone() == Zone.CODE;
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
     * Create or get a manual code project for a folder marked as CODE zone.
     * If a code project already exists for this folder, returns it.
     * Otherwise, creates a new GENERIC type project with statistics from ScannedFile.
     *
     * @param sourceId Source ID
     * @param folderPath Folder path
     * @return Code project DTO
     */
    @Transactional
    public Optional<CodeProjectDto> createOrGetManualCodeProject(UUID sourceId, String folderPath) {
        // Check if project already exists
        Optional<CodeProject> existing = repository.findBySourceIdAndRootPath(sourceId, folderPath);
        if (existing.isPresent()) {
            log.debug("Code project already exists for folder: {}", folderPath);
            return Optional.of(toDto(existing.get()));
        }

        // Get folder statistics from scanned files
        long fileCount = scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath);

        // Only create if there are files in the folder
        if (fileCount == 0) {
            log.debug("No files found in folder {}, skipping code project creation", folderPath);
            return Optional.empty();
        }

        long totalSize = scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath);

        // Extract folder name from path
        // Note: getFileName() returns null for root paths like "/"
        java.nio.file.Path pathObj = Paths.get(folderPath);
        java.nio.file.Path fileNamePath = pathObj.getFileName();
        String folderName = (fileNamePath != null) ? fileNamePath.toString() : "root";
        if (folderName.isEmpty()) {
            folderName = "root";
        }

        // Handle potential integer overflow for file counts
        if (fileCount > Integer.MAX_VALUE) {
            log.warn("File count {} exceeds Integer.MAX_VALUE for folder {}, capping at Integer.MAX_VALUE",
                fileCount, folderPath);
        }
        int safeFileCount = (int) Math.min(fileCount, Integer.MAX_VALUE);

        // Generate stable content hash using UUID from folder path
        String contentHashInput = "manual-" + sourceId + "-" + folderPath;
        String contentHash = UUID.nameUUIDFromBytes(contentHashInput.getBytes(StandardCharsets.UTF_8)).toString();

        // Create new GENERIC code project
        CodeProject project = CodeProject.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .rootPath(folderPath)
            .projectType(ProjectType.GENERIC)
            .name(folderName)
            .version("manual")
            .identifier(folderPath)
            .contentHash(contentHash)
            .sourceFileCount(safeFileCount)
            .totalFileCount(safeFileCount)
            .totalSizeBytes(totalSize)
            .scannedAt(Instant.now())
            .build();

        CodeProject saved = repository.save(project);
        log.info("Created manual code project for folder: {} ({} files, {} bytes)",
            folderPath, fileCount, totalSize);

        return Optional.of(toDto(saved));
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
