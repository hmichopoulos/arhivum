package tech.zaisys.archivum.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.api.dto.CompleteScanRequest;
import tech.zaisys.archivum.api.dto.CreateSourceRequest;
import tech.zaisys.archivum.api.dto.FolderNodeDto;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.dto.SourceResponse;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.service.FolderTreeService;
import tech.zaisys.archivum.server.service.FolderZoneService;
import tech.zaisys.archivum.server.service.SourceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for source management.
 * Provides endpoints for creating and managing source hierarchies.
 */
@RestController
@RequestMapping("/api/sources")
@Slf4j
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;
    private final FolderTreeService folderTreeService;
    private final FolderZoneService folderZoneService;

    /**
     * Create a new source.
     *
     * @param request Source creation request
     * @return Created source DTO
     */
    @PostMapping
    public ResponseEntity<SourceDto> createSource(@RequestBody CreateSourceRequest request) {
        log.info("POST /api/sources - Creating source: {}", request.getName());
        SourceDto created = sourceService.createSource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get all sources.
     *
     * @return List of all sources
     */
    @GetMapping
    public ResponseEntity<List<SourceDto>> getAllSources() {
        log.debug("GET /api/sources - Fetching all sources");
        List<SourceDto> sources = sourceService.findAll();
        return ResponseEntity.ok(sources);
    }

    /**
     * Get source by ID.
     *
     * @param id Source ID
     * @return Source DTO if found
     */
    @GetMapping("/{id}")
    public ResponseEntity<SourceDto> getSourceById(@PathVariable UUID id) {
        log.debug("GET /api/sources/{} - Fetching source", id);
        return sourceService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get folder tree for a source.
     *
     * @param id Source ID
     * @return Folder tree structure
     */
    @GetMapping("/{id}/tree")
    public ResponseEntity<FolderNodeDto> getSourceTree(@PathVariable UUID id) {
        log.debug("GET /api/sources/{}/tree - Building folder tree", id);
        FolderNodeDto tree = folderTreeService.buildTree(id);
        return ResponseEntity.ok(tree);
    }

    /**
     * Update zone classification for a folder.
     *
     * PATCH /api/sources/{id}/folders/zone
     *
     * @param id Source ID
     * @param request Request body containing folder path and zone
     * @return Success response
     */
    @PatchMapping("/{id}/folders/zone")
    public ResponseEntity<?> updateFolderZone(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        String folderPath = request.get("folderPath");
        String zoneStr = request.get("zone");

        log.info("PATCH /api/sources/{}/folders/zone - path={}, zone={}", id, folderPath, zoneStr);

        try {
            Zone zone = Zone.valueOf(zoneStr);
            folderZoneService.setFolderZone(id, folderPath, zone);
            // Invalidate tree cache since zones changed
            folderTreeService.invalidateTree(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            log.error("Invalid zone or source not found: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all root sources (no parent).
     *
     * @return List of root sources
     */
    @GetMapping("/roots")
    public ResponseEntity<List<SourceDto>> getRootSources() {
        log.debug("GET /api/sources/roots - Fetching root sources");
        List<SourceDto> roots = sourceService.findRootSources();
        return ResponseEntity.ok(roots);
    }

    /**
     * Get all child sources of a parent.
     *
     * @param id Parent source ID
     * @return List of child sources
     */
    @GetMapping("/{id}/children")
    public ResponseEntity<List<SourceDto>> getChildren(@PathVariable UUID id) {
        log.debug("GET /api/sources/{}/children - Fetching children", id);
        List<SourceDto> children = sourceService.findChildren(id);
        return ResponseEntity.ok(children);
    }

    /**
     * Link a child source to a parent.
     *
     * @param parentId Parent source ID
     * @param childId Child source ID
     * @return Success response
     */
    @PostMapping("/{parentId}/children/{childId}")
    public ResponseEntity<SourceResponse> linkParentChild(
        @PathVariable UUID parentId,
        @PathVariable UUID childId) {
        log.info("POST /api/sources/{}/children/{} - Linking parent-child", parentId, childId);

        try {
            sourceService.linkParentChild(parentId, childId);
            return ResponseEntity.ok(new SourceResponse(
                parentId,
                true,
                "Successfully linked child to parent"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new SourceResponse(
                parentId,
                false,
                e.getMessage()
            ));
        }
    }

    /**
     * Delete a source and all its children.
     *
     * @param id Source ID to delete
     * @return Success response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<SourceResponse> deleteSource(@PathVariable UUID id) {
        log.info("DELETE /api/sources/{} - Deleting source", id);

        try {
            sourceService.deleteSource(id);
            return ResponseEntity.ok(new SourceResponse(
                id,
                true,
                "Source deleted successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Complete a scan for a source.
     * Updates final statistics and marks as completed.
     *
     * POST /api/sources/{id}/complete
     *
     * @param id Source ID
     * @param request Completion request with final stats
     * @return Updated source DTO
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<SourceDto> completeScan(
            @PathVariable UUID id,
            @RequestBody CompleteScanRequest request) {
        log.info("POST /api/sources/{}/complete - {} files, {} bytes, success={}",
            id, request.getTotalFiles(), request.getTotalSize(), request.getSuccess());

        try {
            SourceDto updated = sourceService.completeScan(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
