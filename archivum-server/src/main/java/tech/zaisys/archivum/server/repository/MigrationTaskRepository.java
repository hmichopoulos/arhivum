package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.zaisys.archivum.api.enums.MigrationTaskStatus;
import tech.zaisys.archivum.server.domain.MigrationTask;

import java.util.List;
import java.util.UUID;

/**
 * Repository for migration tasks.
 */
@Repository
public interface MigrationTaskRepository extends JpaRepository<MigrationTask, UUID> {

    /**
     * Find all tasks for a migration plan.
     */
    List<MigrationTask> findByPlanId(UUID planId);

    /**
     * Find tasks by status.
     */
    List<MigrationTask> findByStatus(MigrationTaskStatus status);

    /**
     * Find tasks for a specific file.
     */
    List<MigrationTask> findByFileId(UUID fileId);

    /**
     * Count tasks by plan and status.
     */
    long countByPlanIdAndStatus(UUID planId, MigrationTaskStatus status);
}
