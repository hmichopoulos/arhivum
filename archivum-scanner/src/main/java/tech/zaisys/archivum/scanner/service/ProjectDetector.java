package tech.zaisys.archivum.scanner.service;

import tech.zaisys.archivum.api.dto.ProjectIdentityDto;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface for detecting code projects.
 * Each implementation can detect a specific type of project (Maven, NPM, Go, etc.)
 */
public interface ProjectDetector {

    /**
     * Check if this detector can handle the given folder.
     * Should be a fast check (just look for marker files).
     *
     * @param folder Folder to check
     * @return true if this detector can handle this folder
     */
    boolean canDetect(Path folder);

    /**
     * Extract project identity from the folder.
     * This may involve parsing build files, so can be slower.
     *
     * @param folder Folder to analyze
     * @return ProjectIdentity if successfully detected, empty otherwise
     */
    Optional<ProjectIdentityDto> detect(Path folder);

    /**
     * Get the priority of this detector.
     * Higher priority detectors run first.
     * Useful when multiple detectors might match (e.g., Maven + Git).
     *
     * @return Priority (higher = run first)
     */
    default int getPriority() {
        return 0;
    }
}
