package tech.zaisys.archivum.server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.server.service.FileBatchResult;
import tech.zaisys.archivum.server.service.FileService;

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
}
