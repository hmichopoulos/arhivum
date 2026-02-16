package tech.zaisys.archivum.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zaisys.archivum.server.api.dto.DestinationDto;
import tech.zaisys.archivum.server.service.DestinationService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for destination management.
 * Provides endpoints for CRUD operations on migration destinations.
 */
@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
@Slf4j
public class DestinationController {

    private final DestinationService destinationService;

    /**
     * Get all destinations with real-time disk space information.
     * GET /api/destinations
     */
    @GetMapping
    public ResponseEntity<List<DestinationDto>> getAllDestinations() {
        log.info("GET /api/destinations");
        List<DestinationDto> destinations = destinationService.getAllDestinations();
        return ResponseEntity.ok(destinations);
    }

    /**
     * Get destination by ID with real-time disk space information.
     * GET /api/destinations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<DestinationDto> getDestinationById(@PathVariable UUID id) {
        log.info("GET /api/destinations/{}", id);
        return destinationService.getDestinationById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all active destinations ordered by priority.
     * GET /api/destinations/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<DestinationDto>> getActiveDestinations() {
        log.info("GET /api/destinations/active");
        List<DestinationDto> destinations = destinationService.getActiveDestinations();
        return ResponseEntity.ok(destinations);
    }

    /**
     * Create a new destination.
     * POST /api/destinations
     */
    @PostMapping
    public ResponseEntity<DestinationDto> createDestination(@RequestBody DestinationDto dto) {
        log.info("POST /api/destinations - Creating destination: {}", dto.getName());
        try {
            DestinationDto created = destinationService.createDestination(dto);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create destination: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update an existing destination.
     * PUT /api/destinations/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<DestinationDto> updateDestination(
        @PathVariable UUID id,
        @RequestBody DestinationDto dto
    ) {
        log.info("PUT /api/destinations/{} - Updating destination", id);
        try {
            DestinationDto updated = destinationService.updateDestination(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update destination {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a destination.
     * DELETE /api/destinations/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDestination(@PathVariable UUID id) {
        log.info("DELETE /api/destinations/{}", id);
        try {
            destinationService.deleteDestination(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to delete destination {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
