package tech.zaisys.archivum.server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.server.service.CodeProjectService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for code project operations.
 */
@RestController
@RequestMapping("/api/code-projects")
@Slf4j
@RequiredArgsConstructor
public class CodeProjectController {

    private final CodeProjectService codeProjectService;

    /**
     * Get all code projects.
     * Returns projects ordered by scan date (most recent first).
     * Excludes projects whose folders have been reclassified to non-CODE zones.
     *
     * @return List of all code projects in CODE zone
     */
    @GetMapping
    public ResponseEntity<List<CodeProjectDto>> getAllProjects() {
        log.info("GET /api/code-projects");
        List<CodeProjectDto> projects = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();
        return ResponseEntity.ok(projects);
    }

    /**
     * Get code project by ID.
     *
     * @param id Project ID
     * @return Code project or 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<CodeProjectDto> getProjectById(@PathVariable UUID id) {
        log.info("GET /api/code-projects/{}", id);
        return codeProjectService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get projects by source ID.
     *
     * @param sourceId Source ID
     * @return List of projects from this source
     */
    @GetMapping("/source/{sourceId}")
    public ResponseEntity<List<CodeProjectDto>> getProjectsBySource(@PathVariable UUID sourceId) {
        log.info("GET /api/code-projects/source/{}", sourceId);
        List<CodeProjectDto> projects = codeProjectService.findBySourceId(sourceId);
        return ResponseEntity.ok(projects);
    }

    /**
     * Get projects by type.
     *
     * @param type Project type (MAVEN, NPM, GO, etc.)
     * @return List of projects of this type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<CodeProjectDto>> getProjectsByType(@PathVariable ProjectType type) {
        log.info("GET /api/code-projects/type/{}", type);
        List<CodeProjectDto> projects = codeProjectService.findByType(type);
        return ResponseEntity.ok(projects);
    }

    /**
     * Get projects by identifier.
     *
     * @param identifier Project identifier
     * @return List of projects with this identifier
     */
    @GetMapping("/identifier/{identifier}")
    public ResponseEntity<List<CodeProjectDto>> getProjectsByIdentifier(@PathVariable String identifier) {
        log.info("GET /api/code-projects/identifier/{}", identifier);
        List<CodeProjectDto> projects = codeProjectService.findByIdentifier(identifier);
        return ResponseEntity.ok(projects);
    }

    /**
     * Get duplicate projects (projects with same content hash).
     * Groups projects by content hash where count > 1.
     * Only includes projects in CODE zone.
     *
     * @return Map of content hash to list of duplicate projects
     */
    @GetMapping("/duplicates")
    public ResponseEntity<Map<String, List<CodeProjectDto>>> getDuplicates() {
        log.info("GET /api/code-projects/duplicates");

        List<CodeProjectDto> allProjects = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Group by content hash
        Map<String, List<CodeProjectDto>> grouped = allProjects.stream()
            .collect(Collectors.groupingBy(CodeProjectDto::getContentHash));

        // Filter to only groups with > 1 project (duplicates)
        Map<String, List<CodeProjectDto>> duplicates = grouped.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ResponseEntity.ok(duplicates);
    }

    /**
     * Create a new code project.
     *
     * @param project Code project DTO
     * @return Created project
     */
    @PostMapping
    public ResponseEntity<CodeProjectDto> createProject(@Valid @RequestBody CodeProjectDto project) {
        log.info("POST /api/code-projects - Creating project: {}", project.getIdentity().getIdentifier());
        CodeProjectDto created = codeProjectService.save(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Create multiple code projects (bulk upload).
     * Each project in the list is validated.
     *
     * @param projects List of code project DTOs
     * @return List of created projects
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<CodeProjectDto>> createProjects(@RequestBody List<@Valid CodeProjectDto> projects) {
        log.info("POST /api/code-projects/bulk - Creating {} projects", projects.size());
        List<CodeProjectDto> created = codeProjectService.saveAll(projects);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Delete code project by ID.
     *
     * @param id Project ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        log.info("DELETE /api/code-projects/{}", id);
        codeProjectService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get statistics about code projects.
     * Only includes projects in CODE zone.
     *
     * @return Statistics map
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.info("GET /api/code-projects/stats");

        List<CodeProjectDto> allProjects = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Count by type
        Map<ProjectType, Long> byType = allProjects.stream()
            .collect(Collectors.groupingBy(
                p -> p.getIdentity().getType(),
                Collectors.counting()
            ));

        // Total statistics
        Map<String, Object> stats = Map.of(
            "total", allProjects.size(),
            "byType", byType,
            "totalSourceFiles", allProjects.stream()
                .mapToInt(CodeProjectDto::getSourceFileCount)
                .sum(),
            "totalSize", allProjects.stream()
                .mapToLong(CodeProjectDto::getTotalSizeBytes)
                .sum()
        );

        return ResponseEntity.ok(stats);
    }
}
