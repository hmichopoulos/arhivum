package tech.zaisys.archivum.server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.service.FileBatchResult;
import tech.zaisys.archivum.server.service.FileService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for file operations.
 * Handles file batch ingestion and retrieval.
 */
@RestController
@RequestMapping("/api/files")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * Ingest a batch of files from scanner.
     *
     * POST /api/files/batch
     *
     * @param batch Batch of files to ingest
     * @return Batch ingestion result with success/failure counts, or error message on validation failure
     */
    @PostMapping("/batch")
    public ResponseEntity<?> ingestBatch(@Valid @RequestBody FileBatchDto batch) {
        log.info("POST /api/files/batch - Ingesting {} files for source {} (batch {})",
            batch.getFiles().size(), batch.getSourceId(), batch.getBatchNumber());

        try {
            FileBatchResult result = fileService.ingestBatch(batch);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            log.error("Batch ingestion failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Query files with optional filters.
     *
     * GET /api/files?sourceId={id}&extension={ext}&isDuplicate={bool}&page={n}&size={m}
     *
     * @param sourceId Source ID (required)
     * @param extension File extension filter (optional)
     * @param isDuplicate Duplicate filter (optional)
     * @param page Page number, 0-indexed (default: 0)
     * @param size Page size (default: 100)
     * @return List of file DTOs
     */
    @GetMapping
    public ResponseEntity<List<FileDto>> queryFiles(
            @RequestParam UUID sourceId,
            @RequestParam(required = false) String extension,
            @RequestParam(required = false) Boolean isDuplicate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        log.debug("GET /api/files?sourceId={}&extension={}&isDuplicate={}&page={}&size={}",
            sourceId, extension, isDuplicate, page, size);

        List<FileDto> files = fileService.queryFiles(
            sourceId, extension, isDuplicate, page, size);

        return ResponseEntity.ok(files);
    }

    /**
     * Get file by ID.
     *
     * GET /api/files/{id}
     *
     * @param id File ID
     * @return File DTO if found, 404 otherwise
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileDto> getFileById(@PathVariable UUID id) {
        log.debug("GET /api/files/{}", id);

        return fileService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all files with the same SHA-256 hash (duplicates).
     *
     * GET /api/files/{id}/duplicates
     *
     * @param id File ID to find duplicates for
     * @return List of all files with the same hash
     */
    @GetMapping("/{id}/duplicates")
    public ResponseEntity<List<FileDto>> getDuplicates(@PathVariable UUID id) {
        log.debug("GET /api/files/{}/duplicates", id);

        List<FileDto> duplicates = fileService.findDuplicates(id);
        return ResponseEntity.ok(duplicates);
    }

    /**
     * Update the zone classification for a file.
     *
     * PATCH /api/files/{id}/zone
     *
     * @param id File ID
     * @param request Request body containing the new zone
     * @return Updated file DTO
     */
    @PatchMapping("/{id}/zone")
    public ResponseEntity<FileDto> updateZone(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        log.info("PATCH /api/files/{}/zone - zone={}", id, request.get("zone"));

        try {
            Zone zone = Zone.valueOf(request.get("zone"));
            FileDto updated = fileService.updateZone(id, zone);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Invalid zone or file not found: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
