package tech.zaisys.archivum.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.server.domain.MigrationPlan;
import tech.zaisys.archivum.server.domain.MigrationTask;
import tech.zaisys.archivum.server.service.MigrationExecutionService;
import tech.zaisys.archivum.server.service.MigrationPlanningService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for migration management.
 * Provides endpoints for creating and executing migration plans.
 */
@RestController
@RequestMapping("/api/migrations")
@Slf4j
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationPlanningService planningService;
    private final MigrationExecutionService executionService;

    /**
     * Create a new migration plan.
     *
     * POST /api/migrations/plans
     *
     * @param request Plan creation request
     * @return Created migration plan
     */
    @PostMapping("/plans")
    public ResponseEntity<MigrationPlan> createPlan(@RequestBody CreatePlanRequest request) {
        log.info("POST /api/migrations/plans - Creating plan: {}", request.name());

        try {
            MigrationPlan plan = planningService.createPlan(
                request.name(),
                request.fileIds(),
                request.destinationType(),
                request.destinationBasePath()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(plan);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all migration plans.
     *
     * GET /api/migrations/plans
     *
     * @return List of all migration plans
     */
    @GetMapping("/plans")
    public ResponseEntity<List<MigrationPlan>> getAllPlans() {
        log.debug("GET /api/migrations/plans - Fetching all plans");
        List<MigrationPlan> plans = planningService.getAllPlans();
        return ResponseEntity.ok(plans);
    }

    /**
     * Get a migration plan by ID.
     *
     * GET /api/migrations/plans/{id}
     *
     * @param id Plan ID
     * @return Migration plan
     */
    @GetMapping("/plans/{id}")
    public ResponseEntity<MigrationPlan> getPlan(@PathVariable UUID id) {
        log.debug("GET /api/migrations/plans/{} - Fetching plan", id);

        try {
            MigrationPlan plan = planningService.getPlan(id);
            return ResponseEntity.ok(plan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all tasks for a migration plan.
     *
     * GET /api/migrations/plans/{id}/tasks
     *
     * @param id Plan ID
     * @return List of migration tasks
     */
    @GetMapping("/plans/{id}/tasks")
    public ResponseEntity<List<MigrationTask>> getPlanTasks(@PathVariable UUID id) {
        log.debug("GET /api/migrations/plans/{}/tasks - Fetching tasks", id);

        try {
            List<MigrationTask> tasks = planningService.getPlanTasks(id);
            return ResponseEntity.ok(tasks);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Mark a plan as ready for execution.
     *
     * POST /api/migrations/plans/{id}/ready
     *
     * @param id Plan ID
     * @return Updated plan
     */
    @PostMapping("/plans/{id}/ready")
    public ResponseEntity<MigrationPlan> markPlanReady(@PathVariable UUID id) {
        log.info("POST /api/migrations/plans/{}/ready - Marking plan as ready", id);

        try {
            MigrationPlan plan = planningService.markPlanReady(id);
            return ResponseEntity.ok(plan);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Execute a migration plan.
     *
     * POST /api/migrations/plans/{id}/execute
     *
     * @param id Plan ID
     * @return Success response
     */
    @PostMapping("/plans/{id}/execute")
    public ResponseEntity<Map<String, Object>> executePlan(@PathVariable UUID id) {
        log.info("POST /api/migrations/plans/{}/execute - Executing plan", id);

        try {
            // Execute in background (could be async in the future)
            executionService.executePlan(id);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Migration plan execution started"
            ));

        } catch (IllegalArgumentException e) {
            log.error("Plan not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();

        } catch (IllegalStateException e) {
            log.error("Cannot execute plan: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get migration progress for a plan.
     *
     * GET /api/migrations/plans/{id}/progress
     *
     * @param id Plan ID
     * @return Migration progress
     */
    @GetMapping("/plans/{id}/progress")
    public ResponseEntity<MigrationExecutionService.MigrationProgress> getProgress(@PathVariable UUID id) {
        log.debug("GET /api/migrations/plans/{}/progress - Getting progress", id);

        try {
            MigrationExecutionService.MigrationProgress progress = executionService.getProgress(id);
            return ResponseEntity.ok(progress);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a migration plan (only DRAFT plans).
     *
     * DELETE /api/migrations/plans/{id}
     *
     * @param id Plan ID
     * @return Success response
     */
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Map<String, Object>> deletePlan(@PathVariable UUID id) {
        log.info("DELETE /api/migrations/plans/{} - Deleting plan", id);

        try {
            planningService.deletePlan(id);
            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Request DTO for creating a migration plan.
     */
    public record CreatePlanRequest(
        String name,
        List<UUID> fileIds,
        String destinationType,
        String destinationBasePath
    ) {}
}
