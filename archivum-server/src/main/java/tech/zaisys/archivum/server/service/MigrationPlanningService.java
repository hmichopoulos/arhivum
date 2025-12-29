package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.enums.FileState;
import tech.zaisys.archivum.api.enums.MigrationStatus;
import tech.zaisys.archivum.api.enums.MigrationTaskStatus;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.domain.MigrationPlan;
import tech.zaisys.archivum.server.domain.MigrationTask;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.repository.MigrationPlanRepository;
import tech.zaisys.archivum.server.repository.MigrationTaskRepository;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating and managing migration plans.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationPlanningService {

    private final ScannedFileRepository fileRepository;
    private final MigrationPlanRepository planRepository;
    private final MigrationTaskRepository taskRepository;

    /**
     * Create a migration plan for selected files.
     *
     * @param name Plan name
     * @param fileIds List of file IDs to migrate
     * @param destinationType Destination type (NAS, WAREHOUSE, GIT)
     * @param destinationBasePath Base destination path
     * @return Created migration plan
     */
    @Transactional
    public MigrationPlan createPlan(String name, List<UUID> fileIds, String destinationType, String destinationBasePath) {
        log.info("Creating migration plan: {} for {} files to {}", name, fileIds.size(), destinationType);

        // Fetch files to migrate
        List<ScannedFile> files = fileRepository.findAllById(fileIds);

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files found with provided IDs");
        }

        // Calculate statistics
        long totalSize = files.stream()
            .mapToLong(ScannedFile::getSize)
            .sum();

        // Create plan
        MigrationPlan plan = MigrationPlan.builder()
            .name(name)
            .status(MigrationStatus.DRAFT)
            .totalFiles(files.size())
            .totalSizeBytes(totalSize)
            .destinationType(destinationType)
            .destinationBasePath(destinationBasePath)
            .createdAt(Instant.now())
            .build();

        plan = planRepository.save(plan);

        // Create tasks for each file, checking for duplicates against PINNED files
        List<MigrationTask> tasks = new ArrayList<>();
        int skippedCount = 0;

        for (ScannedFile file : files) {
            MigrationTask task = createTask(plan, file, destinationBasePath);

            if (task != null) {
                tasks.add(task);
            } else {
                skippedCount++;
            }
        }

        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
        }

        log.info("Created migration plan {} with {} tasks ({} files skipped as duplicates)",
            plan.getId(), tasks.size(), skippedCount);

        return plan;
    }

    /**
     * Create a migration task for a file.
     * Returns null if file is already in archive (duplicate of PINNED file).
     *
     * @param plan Migration plan
     * @param file File to migrate
     * @param destinationBasePath Base destination path
     * @return Created migration task, or null if file should be skipped
     */
    private MigrationTask createTask(MigrationPlan plan, ScannedFile file, String destinationBasePath) {
        // Check if file already exists in PINNED location (already in archive)
        Optional<ScannedFile> pinnedFile = fileRepository.findFirstBySha256AndState(
            file.getSha256(),
            FileState.PINNED
        );

        if (pinnedFile.isPresent()) {
            // File already exists in archive, skip migration
            ScannedFile existingFile = pinnedFile.get();

            log.info("File {} already exists at {} (PINNED), skipping migration",
                file.getPath(), existingFile.getPath());

            // Mark the source file as duplicate
            file.setDuplicateOf(existingFile);
            fileRepository.save(file);

            // Don't create a migration task
            return null;
        }

        // Determine destination path based on file zone and organization rules
        String destinationPath = determineDestinationPath(file, destinationBasePath);

        return MigrationTask.builder()
            .planId(plan.getId())
            .sourceId(file.getSource().getId())
            .fileId(file.getId())
            .destinationPath(destinationPath)
            .status(MigrationTaskStatus.PENDING)
            .build();
    }

    /**
     * Determine destination path for a file based on organization rules.
     *
     * @param file File to migrate
     * @param basePath Base destination path
     * @return Destination path
     */
    private String determineDestinationPath(ScannedFile file, String basePath) {
        // Simple organization: basePath/zone/relativePath
        // TODO: Implement more sophisticated organization rules

        Zone zone = file.getZone();
        String relativePath = file.getPath();

        // Example: /mnt/nas/Archive/DOCUMENTS/path/to/file.pdf
        Path destPath = Paths.get(basePath, zone.name(), relativePath);

        return destPath.toString();
    }

    /**
     * Mark a plan as ready for execution.
     *
     * @param planId Plan ID
     * @return Updated plan
     */
    @Transactional
    public MigrationPlan markPlanReady(UUID planId) {
        MigrationPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        plan.setStatus(MigrationStatus.READY);
        plan = planRepository.save(plan);

        log.info("Marked plan {} as READY", planId);

        return plan;
    }

    /**
     * Get all migration plans.
     *
     * @return List of all plans
     */
    @Transactional(readOnly = true)
    public List<MigrationPlan> getAllPlans() {
        return planRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get a migration plan by ID.
     *
     * @param planId Plan ID
     * @return Migration plan
     */
    @Transactional(readOnly = true)
    public MigrationPlan getPlan(UUID planId) {
        return planRepository.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
    }

    /**
     * Get all tasks for a migration plan.
     *
     * @param planId Plan ID
     * @return List of tasks
     */
    @Transactional(readOnly = true)
    public List<MigrationTask> getPlanTasks(UUID planId) {
        return taskRepository.findByPlanId(planId);
    }

    /**
     * Delete a migration plan (only if DRAFT).
     *
     * @param planId Plan ID
     */
    @Transactional
    public void deletePlan(UUID planId) {
        MigrationPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (plan.getStatus() != MigrationStatus.DRAFT) {
            throw new IllegalStateException("Can only delete DRAFT plans");
        }

        planRepository.delete(plan);
        log.info("Deleted migration plan {}", planId);
    }
}
