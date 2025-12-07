package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.server.domain.CodeProject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CodeProject entities.
 */
@Repository
public interface CodeProjectRepository extends JpaRepository<CodeProject, UUID> {

    /**
     * Find all projects by source ID.
     */
    List<CodeProject> findBySourceId(UUID sourceId);

    /**
     * Find all projects by project type.
     */
    List<CodeProject> findByProjectType(ProjectType projectType);

    /**
     * Find all projects by identifier.
     */
    List<CodeProject> findByIdentifier(String identifier);

    /**
     * Find project by source ID and root path.
     */
    Optional<CodeProject> findBySourceIdAndRootPath(UUID sourceId, String rootPath);

    /**
     * Find all projects with the same content hash (exact duplicates).
     */
    List<CodeProject> findByContentHash(String contentHash);

    /**
     * Find projects by identifier pattern (for finding similar projects).
     */
    @Query("SELECT cp FROM CodeProject cp WHERE cp.identifier LIKE %:pattern%")
    List<CodeProject> findByIdentifierContaining(String pattern);

    /**
     * Count projects by type.
     */
    long countByProjectType(ProjectType projectType);

    /**
     * Find all projects ordered by scanned date (most recent first).
     */
    List<CodeProject> findAllByOrderByScannedAtDesc();
}
