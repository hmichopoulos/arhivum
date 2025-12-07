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
 * Detects Go modules by looking for go.mod.
 * Extracts module path.
 */
@Slf4j
public class GoProjectDetector implements ProjectDetector {

    private static final String MARKER_FILE = "go.mod";
    private static final Pattern MODULE_PATTERN = Pattern.compile("module\\s+([^\\s]+)");

    @Override
    public boolean canDetect(Path folder) {
        return Files.exists(folder.resolve(MARKER_FILE));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        Path goMod = folder.resolve(MARKER_FILE);
        if (!Files.exists(goMod)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(goMod);
            Matcher matcher = MODULE_PATTERN.matcher(content);

            if (matcher.find()) {
                String modulePath = matcher.group(1);

                ProjectIdentityDto identity = ProjectIdentityDto.builder()
                    .type(ProjectType.GO)
                    .name(modulePath)
                    .identifier(modulePath) // For Go, module path is the identifier
                    .build();

                log.debug("Detected Go module: {}", modulePath);
                return Optional.of(identity);
            } else {
                log.warn("Could not find module declaration in go.mod at {}", folder);
                return Optional.empty();
            }

        } catch (IOException e) {
            log.warn("Failed to read go.mod at {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPriority() {
        return 10; // Higher than generic detectors
    }
}
