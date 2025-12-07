package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Python projects by looking for setup.py or pyproject.toml.
 * Extracts name and version.
 */
@Slf4j
public class PythonProjectDetector implements ProjectDetector {

    private static final Pattern SETUP_NAME_PATTERN = Pattern.compile("name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern SETUP_VERSION_PATTERN = Pattern.compile("version\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern TOML_NAME_PATTERN = Pattern.compile("name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern TOML_VERSION_PATTERN = Pattern.compile("version\\s*=\\s*['\"]([^'\"]+)['\"]");

    @Override
    public boolean canDetect(Path folder) {
        return Files.exists(folder.resolve("setup.py")) ||
               Files.exists(folder.resolve("pyproject.toml")) ||
               Files.exists(folder.resolve("requirements.txt"));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        // Try pyproject.toml first (modern Python projects)
        Optional<ProjectIdentityDto> tomlResult = detectFromPyProjectToml(folder);
        if (tomlResult.isPresent()) {
            return tomlResult;
        }

        // Fall back to setup.py
        Optional<ProjectIdentityDto> setupResult = detectFromSetupPy(folder);
        if (setupResult.isPresent()) {
            return setupResult;
        }

        // If only requirements.txt exists, use folder name
        if (Files.exists(folder.resolve("requirements.txt"))) {
            String name = folder.getFileName().toString();
            String identifier = name + ":unknown";

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.PYTHON)
                .name(name)
                .version("unknown")
                .identifier(identifier)
                .build();

            log.debug("Detected Python project (from requirements.txt): {}", identifier);
            return Optional.of(identity);
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 10; // Higher than generic detectors
    }

    /**
     * Detect from pyproject.toml
     */
    private Optional<ProjectIdentityDto> detectFromPyProjectToml(Path folder) {
        Path pyprojectToml = folder.resolve("pyproject.toml");
        if (!Files.exists(pyprojectToml)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(pyprojectToml);

            Optional<String> name = extractPattern(content, TOML_NAME_PATTERN);
            Optional<String> version = extractPattern(content, TOML_VERSION_PATTERN);

            if (name.isEmpty()) {
                return Optional.empty();
            }

            String identifier = name.get() + ":" + version.orElse("unknown");

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.PYTHON)
                .name(name.get())
                .version(version.orElse("unknown"))
                .identifier(identifier)
                .build();

            log.debug("Detected Python project (pyproject.toml): {}", identifier);
            return Optional.of(identity);

        } catch (IOException e) {
            log.debug("Failed to read pyproject.toml at {}", folder);
            return Optional.empty();
        }
    }

    /**
     * Detect from setup.py
     */
    private Optional<ProjectIdentityDto> detectFromSetupPy(Path folder) {
        Path setupPy = folder.resolve("setup.py");
        if (!Files.exists(setupPy)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(setupPy);

            Optional<String> name = extractPattern(content, SETUP_NAME_PATTERN);
            Optional<String> version = extractPattern(content, SETUP_VERSION_PATTERN);

            if (name.isEmpty()) {
                return Optional.empty();
            }

            String identifier = name.get() + ":" + version.orElse("unknown");

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.PYTHON)
                .name(name.get())
                .version(version.orElse("unknown"))
                .identifier(identifier)
                .build();

            log.debug("Detected Python project (setup.py): {}", identifier);
            return Optional.of(identity);

        } catch (IOException e) {
            log.debug("Failed to read setup.py at {}", folder);
            return Optional.empty();
        }
    }

    /**
     * Extract value using regex pattern
     */
    private Optional<String> extractPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
