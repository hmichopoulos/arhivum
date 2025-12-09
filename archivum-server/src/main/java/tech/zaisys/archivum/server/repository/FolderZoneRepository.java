package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.server.domain.FolderZone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing folder zone classifications.
 */
@Repository
public interface FolderZoneRepository extends JpaRepository<FolderZone, UUID> {

    /**
     * Find folder zone by source and exact folder path.
     */
    Optional<FolderZone> findBySourceIdAndFolderPath(UUID sourceId, String folderPath);

    /**
     * Find all folder zones for a source.
     */
    List<FolderZone> findBySourceId(UUID sourceId);

    /**
     * Delete folder zone by source and path.
     */
    @Modifying
    @Transactional
    void deleteBySourceIdAndFolderPath(UUID sourceId, String folderPath);
}
