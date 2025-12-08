package tech.zaisys.archivum.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.enums.FileStatus;
import tech.zaisys.archivum.server.domain.ScannedFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ScannedFile entities.
 * Provides data access methods for file metadata.
 */
@Repository
public interface ScannedFileRepository extends JpaRepository<ScannedFile, UUID> {

    /**
     * Find all files for a given source.
     *
     * @param sourceId Source ID
     * @return List of files
     */
    List<ScannedFile> findBySourceId(UUID sourceId);

    /**
     * Find all files for a given source with pagination.
     *
     * @param sourceId Source ID
     * @param pageable Pagination parameters
     * @return Page of files
     */
    Page<ScannedFile> findBySourceId(UUID sourceId, Pageable pageable);

    /**
     * Count files by source.
     *
     * @param sourceId Source ID
     * @return File count
     */
    long countBySourceId(UUID sourceId);

    /**
     * Find file by source and path (unique combination).
     *
     * @param sourceId Source ID
     * @param path File path
     * @return Optional file
     */
    Optional<ScannedFile> findBySourceIdAndPath(UUID sourceId, String path);

    /**
     * Find all files with a specific hash.
     * Used for duplicate detection.
     *
     * @param sha256 SHA-256 hash
     * @return List of files with this hash
     */
    List<ScannedFile> findBySha256(String sha256);

    /**
     * Find all files with a specific hash, excluding a given file ID.
     * Useful when checking if other duplicates exist.
     *
     * @param hash SHA-256 hash
     * @param excludeId File ID to exclude
     * @return List of files
     */
    @Query("SELECT f FROM ScannedFile f WHERE f.sha256 = :hash AND f.id != :excludeId")
    List<ScannedFile> findBySha256ExcludingId(@Param("hash") String hash,
                                                @Param("excludeId") UUID excludeId);

    /**
     * Check if a hash already exists in the database.
     * Faster than findBySha256 when you only need to know if duplicates exist.
     *
     * @param sha256 SHA-256 hash
     * @return true if hash exists
     */
    boolean existsBySha256(String sha256);

    /**
     * Count files with a specific hash.
     *
     * @param sha256 SHA-256 hash
     * @return Count of files with this hash
     */
    long countBySha256(String sha256);

    /**
     * Find all files with a specific status.
     *
     * @param status File status
     * @return List of files
     */
    List<ScannedFile> findByStatus(FileStatus status);

    /**
     * Find all files with a specific status for a source.
     *
     * @param sourceId Source ID
     * @param status File status
     * @return List of files
     */
    List<ScannedFile> findBySourceIdAndStatus(UUID sourceId, FileStatus status);

    /**
     * Find all duplicate files.
     *
     * @return List of duplicate files
     */
    List<ScannedFile> findByIsDuplicateTrue();

    /**
     * Find all duplicate files for a source.
     *
     * @param sourceId Source ID
     * @return List of duplicate files
     */
    List<ScannedFile> findBySourceIdAndIsDuplicateTrue(UUID sourceId);

    /**
     * Find files by extension.
     *
     * @param extension File extension (lowercase, without dot)
     * @return List of files
     */
    List<ScannedFile> findByExtension(String extension);

    /**
     * Find files by MIME type.
     *
     * @param mimeType MIME type
     * @return List of files
     */
    List<ScannedFile> findByMimeType(String mimeType);

    /**
     * Find all files ordered by scanned time (most recent first).
     *
     * @return List of files
     */
    List<ScannedFile> findAllByOrderByScannedAtDesc();

    /**
     * Delete all files for a given source.
     * Useful when re-scanning or removing a source.
     *
     * @param sourceId Source ID
     */
    @Modifying
    @Transactional
    void deleteBySourceId(UUID sourceId);
}
