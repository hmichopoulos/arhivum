package tech.zaisys.archivum.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.server.domain.Source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Source entities.
 * Provides queries for hierarchical source relationships.
 */
@Repository
public interface SourceRepository extends JpaRepository<Source, UUID> {

    /**
     * Find all child sources of a given parent.
     *
     * @param parentId Parent source ID
     * @return List of child sources
     */
    @Query("SELECT s FROM Source s WHERE s.parent.id = :parentId")
    List<Source> findByParentId(UUID parentId);

    /**
     * Find all root sources (sources with no parent).
     *
     * @return List of root sources
     */
    List<Source> findByParentIsNull();

    /**
     * Find source by ID with children eagerly fetched.
     *
     * @param id Source ID
     * @return Optional containing source with children if found
     */
    @Query("SELECT s FROM Source s LEFT JOIN FETCH s.children WHERE s.id = :id")
    Optional<Source> findByIdWithChildren(UUID id);

    /**
     * Find all sources by type.
     *
     * @param type Source type
     * @return List of sources of the given type
     */
    List<Source> findByType(SourceType type);

    /**
     * Find all sources ordered by creation date (most recent first).
     *
     * @return List of sources ordered by creation date
     */
    List<Source> findAllByOrderByCreatedAtDesc();
}
