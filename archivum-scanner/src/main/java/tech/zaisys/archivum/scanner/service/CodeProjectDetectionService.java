package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service that coordinates multiple ProjectDetectors to identify code projects.
 * Runs detectors in priority order and returns the first match.
 */
@Slf4j
public class CodeProjectDetectionService {

    private final List<ProjectDetector> detectors;

    public CodeProjectDetectionService() {
        // Initialize all detectors in a mutable list
        this.detectors = new java.util.ArrayList<>(List.of(
            new MavenProjectDetector(),
            new GradleProjectDetector(),
            new NpmProjectDetector(),
            new GoProjectDetector(),
            new PythonProjectDetector(),
            new RustProjectDetector(),
            new GitProjectDetector(),
            new GenericCodeDetector()
        ));

        // Sort by priority (highest first)
        this.detectors.sort(Comparator.comparingInt(ProjectDetector::getPriority).reversed());

        log.info("Initialized code project detection with {} detectors", detectors.size());
    }

    /**
     * Detect code project in the given folder.
     * Runs detectors in priority order and returns first match.
     *
     * @param folder Folder to analyze
     * @return ProjectIdentity if detected, empty otherwise
     */
    public Optional<ProjectIdentityDto> detectProject(Path folder) {
        for (ProjectDetector detector : detectors) {
            if (detector.canDetect(folder)) {
                Optional<ProjectIdentityDto> result = detector.detect(folder);
                if (result.isPresent()) {
                    log.debug("Detected project at {} using {}", folder, detector.getClass().getSimpleName());
                    return result;
                }
            }
        }

        log.debug("No code project detected at {}", folder);
        return Optional.empty();
    }

    /**
     * Check if a folder is a code project (without extracting full metadata).
     * Useful for quick checks during scanning.
     *
     * @param folder Folder to check
     * @return true if this appears to be a code project
     */
    public boolean isCodeProject(Path folder) {
        return detectors.stream().anyMatch(detector -> detector.canDetect(folder));
    }
}
