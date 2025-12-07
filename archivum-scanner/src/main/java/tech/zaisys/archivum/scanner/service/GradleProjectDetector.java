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
 * Detects Gradle projects by looking for build.gradle or build.gradle.kts.
 * Extracts group, name (from settings.gradle), and version.
 */
@Slf4j
public class GradleProjectDetector implements ProjectDetector {

    private static final Pattern GROUP_PATTERN = Pattern.compile("group\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_PATTERN = Pattern.compile("version\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern ROOT_PROJECT_NAME_PATTERN = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");

    @Override
    public boolean canDetect(Path folder) {
        return Files.exists(folder.resolve("build.gradle")) ||
               Files.exists(folder.resolve("build.gradle.kts"));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        Path buildGradle = Files.exists(folder.resolve("build.gradle"))
            ? folder.resolve("build.gradle")
            : folder.resolve("build.gradle.kts");

        if (!Files.exists(buildGradle)) {
            return Optional.empty();
        }

        try {
            String buildContent = Files.readString(buildGradle);

            // Extract group and version from build.gradle
            String groupId = extractPattern(buildContent, GROUP_PATTERN).orElse("unknown");
            String version = extractPattern(buildContent, VERSION_PATTERN).orElse("unknown");

            // Extract project name from settings.gradle or settings.gradle.kts
            String name = extractProjectName(folder).orElse(folder.getFileName().toString());

            String identifier = groupId + ":" + name + ":" + version;

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.GRADLE)
                .name(name)
                .version(version)
                .groupId(groupId)
                .identifier(identifier)
                .build();

            log.debug("Detected Gradle project: {}", identifier);
            return Optional.of(identity);

        } catch (IOException e) {
            log.warn("Failed to read Gradle build file at {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPriority() {
        return 10; // Higher than generic detectors
    }

    /**
     * Extract project name from settings.gradle or settings.gradle.kts
     */
    private Optional<String> extractProjectName(Path folder) {
        Path settingsGradle = Files.exists(folder.resolve("settings.gradle"))
            ? folder.resolve("settings.gradle")
            : folder.resolve("settings.gradle.kts");

        if (!Files.exists(settingsGradle)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(settingsGradle);
            return extractPattern(content, ROOT_PROJECT_NAME_PATTERN);
        } catch (IOException e) {
            log.debug("Failed to read settings.gradle at {}", folder);
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
