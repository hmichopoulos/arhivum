package tech.zaisys.archivum.scanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects NPM projects by looking for package.json.
 * Extracts name and version.
 */
@Slf4j
public class NpmProjectDetector implements ProjectDetector {

    private static final String MARKER_FILE = "package.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean canDetect(Path folder) {
        return Files.exists(folder.resolve(MARKER_FILE));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        Path packageJson = folder.resolve(MARKER_FILE);
        if (!Files.exists(packageJson)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(packageJson.toFile());

            String name = root.has("name") ? root.get("name").asText() : null;
            String version = root.has("version") ? root.get("version").asText() : "unknown";

            if (name == null || name.isEmpty()) {
                log.warn("NPM project at {} missing name", folder);
                return Optional.empty();
            }

            String identifier = name + ":" + version;

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.NPM)
                .name(name)
                .version(version)
                .identifier(identifier)
                .build();

            log.debug("Detected NPM project: {}", identifier);
            return Optional.of(identity);

        } catch (Exception e) {
            log.warn("Failed to parse package.json at {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPriority() {
        return 10; // Higher than generic detectors
    }
}
