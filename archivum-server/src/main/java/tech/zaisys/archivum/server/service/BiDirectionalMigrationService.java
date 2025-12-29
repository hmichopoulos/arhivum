package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.enums.FileState;
import tech.zaisys.archivum.api.enums.MigrationType;
import tech.zaisys.archivum.server.domain.FileMigrationHistory;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.repository.FileMigrationHistoryRepository;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;

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
 * Service for bi-directional file migration between NAS and Warehouse.
 * Supports moving files from NAS to Warehouse (free up space) and vice versa (need faster access).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BiDirectionalMigrationService {

    private final ScannedFileRepository fileRepository;
    private final FileMigrationHistoryRepository historyRepository;

    /**
     * Move files from NAS to Warehouse.
     * Frees up NAS space by moving less-used files to warehouse disks.
     *
     * @param fileIds List of file IDs to move
     * @param warehouseBasePath Base path on warehouse disk
     * @return Result summary
     */
    @Transactional
    public MigrationResult moveToWarehouse(List<UUID> fileIds, String warehouseBasePath) {
        log.info("Moving {} files from NAS to Warehouse at {}", fileIds.size(), warehouseBasePath);

        int successCount = 0;
        int failCount = 0;
        long totalSize = 0;

        for (UUID fileId : fileIds) {
            try {
                ScannedFile file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

                // Verify file is currently on NAS (PINNED or MIGRATED)
                if (file.getState() != FileState.PINNED && file.getState() != FileState.MIGRATED) {
                    throw new IllegalStateException("File must be PINNED or MIGRATED to move to warehouse");
                }

                // Get current file path (on NAS)
                String currentPath = file.getMigratedPath() != null ?
                    file.getMigratedPath() : file.getPath();

                // Build warehouse destination path
                String warehousePath = buildWarehousePath(file, warehouseBasePath);

                // Execute migration
                moveFile(currentPath, warehousePath, file);

                // Update file state
                file.setState(FileState.WAREHOUSED);
                file.setMigratedPath(warehousePath);
                fileRepository.save(file);

                // Record history
                recordHistory(file.getId(), currentPath, warehousePath, MigrationType.NAS_TO_WAREHOUSE);

                successCount++;
                totalSize += file.getSize();

                log.info("Moved file {} to warehouse: {} -> {}", file.getName(), currentPath, warehousePath);

            } catch (Exception e) {
                log.error("Failed to move file {} to warehouse: {}", fileId, e.getMessage(), e);
                failCount++;
            }
        }

        log.info("NAS → Warehouse migration complete: {} succeeded, {} failed, {} bytes moved",
            successCount, failCount, totalSize);

        return new MigrationResult(successCount, failCount, totalSize);
    }

    /**
     * Move files from Warehouse to NAS.
     * Brings warehouse files back to NAS for faster access.
     *
     * @param fileIds List of file IDs to move
     * @param nasBasePath Base path on NAS
     * @return Result summary
     */
    @Transactional
    public MigrationResult moveToNAS(List<UUID> fileIds, String nasBasePath) {
        log.info("Moving {} files from Warehouse to NAS at {}", fileIds.size(), nasBasePath);

        int successCount = 0;
        int failCount = 0;
        long totalSize = 0;

        for (UUID fileId : fileIds) {
            try {
                ScannedFile file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

                // Verify file is currently warehoused
                if (file.getState() != FileState.WAREHOUSED) {
                    throw new IllegalStateException("File must be WAREHOUSED to move to NAS");
                }

                // Get current warehouse path
                String warehousePath = file.getMigratedPath();

                // Build NAS destination path
                String nasPath = buildNASPath(file, nasBasePath);

                // Execute migration
                moveFile(warehousePath, nasPath, file);

                // Update file state
                file.setState(FileState.PINNED);  // Now on NAS, pinned in place
                file.setMigratedPath(nasPath);
                fileRepository.save(file);

                // Record history
                recordHistory(file.getId(), warehousePath, nasPath, MigrationType.WAREHOUSE_TO_NAS);

                successCount++;
                totalSize += file.getSize();

                log.info("Moved file {} to NAS: {} -> {}", file.getName(), warehousePath, nasPath);

            } catch (Exception e) {
                log.error("Failed to move file {} to NAS: {}", fileId, e.getMessage(), e);
                failCount++;
            }
        }

        log.info("Warehouse → NAS migration complete: {} succeeded, {} failed, {} bytes moved",
            successCount, failCount, totalSize);

        return new MigrationResult(successCount, failCount, totalSize);
    }

    /**
     * Build warehouse destination path for a file.
     *
     * @param file File to migrate
     * @param warehouseBasePath Base path on warehouse disk
     * @return Full warehouse path
     */
    private String buildWarehousePath(ScannedFile file, String warehouseBasePath) {
        // Organize by zone and original path structure
        Path destPath = Paths.get(
            warehouseBasePath,
            file.getZone().name(),
            file.getPath()
        );

        return destPath.toString();
    }

    /**
     * Build NAS destination path for a file.
     *
     * @param file File to migrate
     * @param nasBasePath Base path on NAS
     * @return Full NAS path
     */
    private String buildNASPath(ScannedFile file, String nasBasePath) {
        // Organize by zone
        Path destPath = Paths.get(
            nasBasePath,
            file.getZone().name(),
            file.getPath()
        );

        return destPath.toString();
    }

    /**
     * Move file and verify checksum.
     *
     * @param sourcePath Source file path
     * @param destPath Destination file path
     * @param file File entity
     */
    private void moveFile(String sourcePath, String destPath, ScannedFile file) throws IOException {
        Path source = Paths.get(sourcePath);
        Path dest = Paths.get(destPath);

        if (!Files.exists(source)) {
            throw new IOException("Source file not found: " + sourcePath);
        }

        // Create destination directory
        Files.createDirectories(dest.getParent());

        // Copy file
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);

        // Verify checksum
        String copiedHash = computeSHA256(dest);
        if (!copiedHash.equalsIgnoreCase(file.getSha256())) {
            Files.deleteIfExists(dest);
            throw new IOException(String.format(
                "Checksum mismatch: expected %s, got %s", file.getSha256(), copiedHash));
        }

        // Delete original after successful verification
        Files.delete(source);

        log.debug("File moved and verified: {} -> {}", sourcePath, destPath);
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
     * Record migration history.
     *
     * @param fileId File ID
     * @param fromLocation Source location
     * @param toLocation Destination location
     * @param migrationType Migration type
     */
    private void recordHistory(UUID fileId, String fromLocation, String toLocation, MigrationType migrationType) {
        FileMigrationHistory history = FileMigrationHistory.builder()
            .fileId(fileId)
            .fromLocation(fromLocation)
            .toLocation(toLocation)
            .migrationType(migrationType)
            .migratedAt(Instant.now())
            .build();

        historyRepository.save(history);
    }

    /**
     * Migration result summary.
     */
    public record MigrationResult(
        int successCount,
        int failCount,
        long totalBytes
    ) {}
}
