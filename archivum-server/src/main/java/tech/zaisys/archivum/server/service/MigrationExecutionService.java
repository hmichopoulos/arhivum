package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.enums.*;
import tech.zaisys.archivum.server.domain.*;
import tech.zaisys.archivum.server.repository.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for executing migration plans.
 * Handles actual file copying and state management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationExecutionService {

    private final MigrationPlanRepository planRepository;
    private final MigrationTaskRepository taskRepository;
    private final SourceRepository sourceRepository;
    private final ScannedFileRepository fileRepository;
    private final FileMigrationHistoryRepository historyRepository;

    /**
     * Execute a migration plan.
     *
     * @param planId Plan ID to execute
     */
    @Transactional
    public void executePlan(UUID planId) {
        log.info("Executing migration plan: {}", planId);

        MigrationPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (plan.getStatus() != MigrationStatus.READY) {
            throw new IllegalStateException("Plan must be in READY state to execute");
        }

        // Update plan status
        plan.setStatus(MigrationStatus.EXECUTING);
        plan.setStartedAt(Instant.now());
        planRepository.save(plan);

        // Get all tasks
        List<MigrationTask> tasks = taskRepository.findByPlanId(planId);

        // Check if all required disks are online
        checkRequiredDisks(tasks);

        // Execute tasks
        int successCount = 0;
        int failCount = 0;

        for (MigrationTask task : tasks) {
            try {
                executeTask(task);
                successCount++;
            } catch (Exception e) {
                log.error("Task {} failed: {}", task.getId(), e.getMessage(), e);
                failCount++;

                task.setStatus(MigrationTaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setCompletedAt(Instant.now());
                taskRepository.save(task);
            }
        }

        // Update plan status
        plan.setCompletedAt(Instant.now());

        if (failCount == 0) {
            plan.setStatus(MigrationStatus.COMPLETE);
            log.info("Migration plan {} completed successfully: {} files", planId, successCount);
        } else {
            plan.setStatus(MigrationStatus.FAILED);
            plan.setErrorMessage(String.format("%d tasks failed, %d succeeded", failCount, successCount));
            log.error("Migration plan {} failed: {} succeeded, {} failed", planId, successCount, failCount);
        }

        planRepository.save(plan);
    }

    /**
     * Check if all required source disks are online.
     *
     * @param tasks List of migration tasks
     */
    private void checkRequiredDisks(List<MigrationTask> tasks) {
        for (MigrationTask task : tasks) {
            Source source = sourceRepository.findById(task.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + task.getSourceId()));

            if (!"ONLINE".equals(source.getDiskState())) {
                throw new IllegalStateException(
                    String.format("Source disk '%s' is OFFLINE. Please connect the disk before executing migration.",
                        source.getName()));
            }
        }
    }

    /**
     * Execute a single migration task.
     *
     * @param task Migration task to execute
     */
    private void executeTask(MigrationTask task) throws IOException {
        log.info("Executing task {}: file {} -> {}", task.getId(), task.getFileId(), task.getDestinationPath());

        // Update task status
        task.setStatus(MigrationTaskStatus.IN_PROGRESS);
        task.setStartedAt(Instant.now());
        taskRepository.save(task);

        // Get file info
        ScannedFile file = fileRepository.findById(task.getFileId())
            .orElseThrow(() -> new IllegalArgumentException("File not found: " + task.getFileId()));

        // Get source disk info
        Source source = sourceRepository.findById(task.getSourceId())
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + task.getSourceId()));

        // Build full source path
        Path sourcePath = Paths.get(source.getMountPoint(), file.getPath());

        if (!Files.exists(sourcePath)) {
            throw new IOException("Source file not found: " + sourcePath);
        }

        // Build destination path
        Path destPath = Paths.get(task.getDestinationPath());

        // Create destination directory
        Files.createDirectories(destPath.getParent());

        // Copy file
        log.debug("Copying: {} -> {}", sourcePath, destPath);
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

        // Verify checksum
        String copiedHash = computeSHA256(destPath);
        if (!copiedHash.equalsIgnoreCase(file.getSha256())) {
            // Checksum mismatch - delete copied file and fail
            Files.deleteIfExists(destPath);
            throw new IOException(String.format(
                "Checksum mismatch: expected %s, got %s", file.getSha256(), copiedHash));
        }

        log.debug("Checksum verified: {}", copiedHash);

        // Update file state
        file.setState(FileState.MIGRATED);
        file.setMigratedPath(destPath.toString());
        fileRepository.save(file);

        // Record migration history
        FileMigrationHistory history = FileMigrationHistory.builder()
            .fileId(file.getId())
            .fromLocation(sourcePath.toString())
            .toLocation(destPath.toString())
            .migrationType(MigrationType.NAS_COPY)
            .migratedAt(Instant.now())
            .build();

        historyRepository.save(history);

        // Update task status
        task.setStatus(MigrationTaskStatus.COMPLETE);
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);

        log.info("Task {} completed successfully", task.getId());
    }

    /**
     * Compute SHA-256 hash of a file.
     *
     * @param file File to hash
     * @return SHA-256 hash in hex format
     */
    private String computeSHA256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();

            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            throw new IOException("Failed to compute SHA-256: " + e.getMessage(), e);
        }
    }

    /**
     * Get migration progress for a plan.
     *
     * @param planId Plan ID
     * @return Progress information
     */
    @Transactional(readOnly = true)
    public MigrationProgress getProgress(UUID planId) {
        MigrationPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        long pending = taskRepository.countByPlanIdAndStatus(planId, MigrationTaskStatus.PENDING);
        long inProgress = taskRepository.countByPlanIdAndStatus(planId, MigrationTaskStatus.IN_PROGRESS);
        long complete = taskRepository.countByPlanIdAndStatus(planId, MigrationTaskStatus.COMPLETE);
        long failed = taskRepository.countByPlanIdAndStatus(planId, MigrationTaskStatus.FAILED);

        return new MigrationProgress(
            planId,
            plan.getStatus(),
            plan.getTotalFiles(),
            complete,
            failed,
            inProgress,
            pending
        );
    }

    /**
     * Migration progress information.
     */
    public record MigrationProgress(
        UUID planId,
        MigrationStatus status,
        int totalTasks,
        long completedTasks,
        long failedTasks,
        long inProgressTasks,
        long pendingTasks
    ) {}
}
