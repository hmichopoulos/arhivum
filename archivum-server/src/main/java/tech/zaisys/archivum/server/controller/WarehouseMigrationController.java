package tech.zaisys.archivum.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.server.service.BiDirectionalMigrationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for warehouse-specific migration operations.
 * Handles bi-directional migrations between NAS and Warehouse.
 */
@RestController
@RequestMapping("/api/warehouse")
@Slf4j
@RequiredArgsConstructor
public class WarehouseMigrationController {

    private final BiDirectionalMigrationService migrationService;

    /**
     * Move files from NAS to Warehouse.
     * Frees up NAS space by moving less-used files to warehouse disks.
     *
     * POST /api/warehouse/move-to-warehouse
     *
     * @param request Request with file IDs and warehouse base path
     * @return Migration result
     */
    @PostMapping("/move-to-warehouse")
    public ResponseEntity<BiDirectionalMigrationService.MigrationResult> moveToWarehouse(
            @RequestBody MoveToWarehouseRequest request) {

        log.info("POST /api/warehouse/move-to-warehouse - Moving {} files to warehouse at {}",
            request.fileIds().size(), request.warehouseBasePath());

        try {
            BiDirectionalMigrationService.MigrationResult result =
                migrationService.moveToWarehouse(request.fileIds(), request.warehouseBasePath());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Move files from Warehouse to NAS.
     * Brings warehouse files back to NAS for faster access.
     *
     * POST /api/warehouse/move-to-nas
     *
     * @param request Request with file IDs and NAS base path
     * @return Migration result
     */
    @PostMapping("/move-to-nas")
    public ResponseEntity<BiDirectionalMigrationService.MigrationResult> moveToNAS(
            @RequestBody MoveToNASRequest request) {

        log.info("POST /api/warehouse/move-to-nas - Moving {} files to NAS at {}",
            request.fileIds().size(), request.nasBasePath());

        try {
            BiDirectionalMigrationService.MigrationResult result =
                migrationService.moveToNAS(request.fileIds(), request.nasBasePath());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get warehouse statistics.
     * Shows how many files are warehoused, total size, etc.
     *
     * GET /api/warehouse/stats
     *
     * @return Warehouse statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getWarehouseStats() {
        log.debug("GET /api/warehouse/stats - Getting warehouse statistics");

        // TODO: Implement warehouse statistics
        // For now, return placeholder
        Map<String, Object> stats = Map.of(
            "totalWarehousedFiles", 0,
            "totalWarehousedSize", 0L,
            "warehouseDisks", 0
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Request DTO for moving files to warehouse.
     */
    public record MoveToWarehouseRequest(
        List<UUID> fileIds,
        String warehouseBasePath
    ) {}

    /**
     * Request DTO for moving files to NAS.
     */
    public record MoveToNASRequest(
        List<UUID> fileIds,
        String nasBasePath
    ) {}
}
