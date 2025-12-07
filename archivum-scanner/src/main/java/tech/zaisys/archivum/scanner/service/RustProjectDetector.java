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
 * Detects Rust projects by looking for Cargo.toml.
 * Extracts name and version.
 */
@Slf4j
public class RustProjectDetector implements ProjectDetector {

    private static final String MARKER_FILE = "Cargo.toml";
    private static final Pattern NAME_PATTERN = Pattern.compile("name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_PATTERN = Pattern.compile("version\\s*=\\s*['\"]([^'\"]+)['\"]");

    @Override
    public boolean canDetect(Path folder) {
        return Files.exists(folder.resolve(MARKER_FILE));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        Path cargoToml = folder.resolve(MARKER_FILE);
        if (!Files.exists(cargoToml)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(cargoToml);

            Optional<String> name = extractPattern(content, NAME_PATTERN);
            Optional<String> version = extractPattern(content, VERSION_PATTERN);

            if (name.isEmpty()) {
                log.warn("Rust project at {} missing name in Cargo.toml", folder);
                return Optional.empty();
            }

            String identifier = name.get() + ":" + version.orElse("unknown");

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.RUST)
                .name(name.get())
                .version(version.orElse("unknown"))
                .identifier(identifier)
                .build();

            log.debug("Detected Rust project: {}", identifier);
            return Optional.of(identity);

        } catch (IOException e) {
            log.warn("Failed to read Cargo.toml at {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPriority() {
        return 10; // Higher than generic detectors
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
