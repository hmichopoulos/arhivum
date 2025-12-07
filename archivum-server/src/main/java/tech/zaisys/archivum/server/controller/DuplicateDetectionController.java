package tech.zaisys.archivum.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup;
import tech.zaisys.archivum.server.service.CodeProjectDuplicateDetectionService;
import tech.zaisys.archivum.server.service.CodeProjectDuplicateDetectionService.SimilarityResult;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for code project duplicate detection.
 */
@RestController
@RequestMapping("/api/code-projects/duplicates")
@Slf4j
@RequiredArgsConstructor
public class DuplicateDetectionController {

    private final CodeProjectDuplicateDetectionService duplicateDetectionService;

    /**
     * Run duplicate detection for all projects.
     * Analyzes all code projects and creates duplicate groups.
     *
     * @return List of duplicate groups found
     */
    @PostMapping("/detect")
    public ResponseEntity<List<CodeProjectDuplicateGroup>> detectDuplicates() {
        log.info("POST /api/code-projects/duplicates/detect - Running duplicate detection");
        List<CodeProjectDuplicateGroup> groups = duplicateDetectionService.detectAllDuplicates();
        return ResponseEntity.ok(groups);
    }

    /**
     * Find duplicates for a specific project.
     *
     * @param projectId Project ID to find duplicates for
     * @return List of duplicate groups containing this project
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<CodeProjectDuplicateGroup>> findDuplicatesForProject(
        @PathVariable UUID projectId
    ) {
        log.info("GET /api/code-projects/duplicates/project/{}", projectId);
        List<CodeProjectDuplicateGroup> groups = duplicateDetectionService.findDuplicatesFor(projectId);
        return ResponseEntity.ok(groups);
    }
}
