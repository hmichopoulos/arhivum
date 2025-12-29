package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.zaisys.archivum.api.enums.MigrationStatus;
import tech.zaisys.archivum.server.domain.MigrationPlan;

import java.util.List;
import java.util.UUID;

/**
 * Repository for migration plans.
 */
@Repository
public interface MigrationPlanRepository extends JpaRepository<MigrationPlan, UUID> {

    /**
     * Find all plans ordered by creation time (newest first).
     */
    List<MigrationPlan> findAllByOrderByCreatedAtDesc();

    /**
     * Find plans by status.
     */
    List<MigrationPlan> findByStatus(MigrationStatus status);
}
