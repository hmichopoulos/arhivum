package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.zaisys.archivum.server.domain.FileMigrationHistory;

import java.util.List;
import java.util.UUID;

/**
 * Repository for file migration history.
 */
@Repository
public interface FileMigrationHistoryRepository extends JpaRepository<FileMigrationHistory, UUID> {

    /**
     * Find all migration history for a specific file.
     */
    List<FileMigrationHistory> findByFileIdOrderByMigratedAtDesc(UUID fileId);
}
