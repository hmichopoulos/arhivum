package tech.zaisys.archivum.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.server.service.DiskDetectionService;
import tech.zaisys.archivum.server.service.SourceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for disk tracking and state management.
 * Provides endpoints for detecting connected disks and managing disk states.
 */
@RestController
@RequestMapping("/api/disks")
@Slf4j
@RequiredArgsConstructor
public class DiskController {

    private final DiskDetectionService diskDetectionService;
    private final SourceService sourceService;

    /**
     * Scan system for connected disks and update database states.
     *
     * POST /api/disks/scan
     *
     * @return Map of detected disk serials to mount points
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, String>> scanDisks() {
        log.info("POST /api/disks/scan - Scanning for connected disks");

        Map<String, DiskDetectionService.DiskInfo> detected = diskDetectionService.detectConnectedDisks();

        // Convert to simpler map for response
        Map<String, String> result = detected.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().mountPoint
            ));

        return ResponseEntity.ok(result);
    }

    /**
     * Get all disks with their current states.
     *
     * GET /api/disks
     *
     * @return List of all sources that represent disks
     */
    @GetMapping
    public ResponseEntity<List<SourceDto>> getAllDisks() {
        log.debug("GET /api/disks - Fetching all disk sources");

        List<SourceDto> sources = sourceService.findAll();

        // Filter to only include sources with serial numbers (actual disks)
        List<SourceDto> disks = sources.stream()
            .filter(s -> s.getSerialNumber() != null && !s.getSerialNumber().isEmpty())
            .toList();

        return ResponseEntity.ok(disks);
    }

    /**
     * Get online disks.
     *
     * GET /api/disks/online
     *
     * @return List of online disk sources
     */
    @GetMapping("/online")
    public ResponseEntity<List<SourceDto>> getOnlineDisks() {
        log.debug("GET /api/disks/online - Fetching online disks");

        List<SourceDto> sources = sourceService.findAll();

        List<SourceDto> onlineDisks = sources.stream()
            .filter(s -> s.getSerialNumber() != null && !s.getSerialNumber().isEmpty())
            .filter(s -> "ONLINE".equals(s.getDiskState()))
            .toList();

        return ResponseEntity.ok(onlineDisks);
    }

    /**
     * Get offline disks.
     *
     * GET /api/disks/offline
     *
     * @return List of offline disk sources
     */
    @GetMapping("/offline")
    public ResponseEntity<List<SourceDto>> getOfflineDisks() {
        log.debug("GET /api/disks/offline - Fetching offline disks");

        List<SourceDto> sources = sourceService.findAll();

        List<SourceDto> offlineDisks = sources.stream()
            .filter(s -> s.getSerialNumber() != null && !s.getSerialNumber().isEmpty())
            .filter(s -> "OFFLINE".equals(s.getDiskState()))
            .toList();

        return ResponseEntity.ok(offlineDisks);
    }

    /**
     * Manually mark a disk as online.
     *
     * POST /api/disks/{id}/online
     *
     * @param id Source ID
     * @param request Request body with mount point
     * @return Success response
     */
    @PostMapping("/{id}/online")
    public ResponseEntity<Map<String, Object>> markDiskOnline(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {

        String mountPoint = request.get("mountPoint");
        if (mountPoint == null || mountPoint.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "mountPoint is required"));
        }

        log.info("POST /api/disks/{}/online - mountPoint={}", id, mountPoint);

        try {
            diskDetectionService.markDiskOnline(id, mountPoint);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Manually mark a disk as offline.
     *
     * POST /api/disks/{id}/offline
     *
     * @param id Source ID
     * @return Success response
     */
    @PostMapping("/{id}/offline")
    public ResponseEntity<Map<String, Object>> markDiskOffline(@PathVariable UUID id) {
        log.info("POST /api/disks/{}/offline", id);

        try {
            diskDetectionService.markDiskOffline(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
