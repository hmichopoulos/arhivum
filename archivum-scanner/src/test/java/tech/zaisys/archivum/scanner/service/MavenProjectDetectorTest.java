package tech.zaisys.archivum.scanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Maven project detection.
 */
class MavenProjectDetectorTest {

    private MavenProjectDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new MavenProjectDetector();
    }

    @Test
    void testCanDetect_WithPomXml() throws IOException {
        // Given: A directory with pom.xml
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, "<project></project>");

        // When
        boolean result = detector.canDetect(tempDir);

        // Then
        assertTrue(result, "Should detect directory with pom.xml");
    }

    @Test
    void testCanDetect_WithoutPomXml() {
        // Given: A directory without pom.xml
        // When
        boolean result = detector.canDetect(tempDir);

        // Then
        assertFalse(result, "Should not detect directory without pom.xml");
    }

    @Test
    void testDetect_ValidPom() throws IOException {
        // Given: A valid pom.xml
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>com.example</groupId>
                <artifactId>my-project</artifactId>
                <version>1.0.0</version>
            </project>
            """;

        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertTrue(result.isPresent(), "Should detect Maven project");
        ProjectIdentityDto identity = result.get();
        assertEquals(ProjectType.MAVEN, identity.getType());
        assertEquals("my-project", identity.getName());
        assertEquals("1.0.0", identity.getVersion());
        assertEquals("com.example", identity.getGroupId());
        assertEquals("com.example:my-project:1.0.0", identity.getIdentifier());
    }

    @Test
    void testDetect_WithParentPom() throws IOException {
        // Given: A pom.xml with parent (inherits groupId and version)
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>2.0.0</version>
                </parent>
                <artifactId>my-module</artifactId>
            </project>
            """;

        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertTrue(result.isPresent());
        ProjectIdentityDto identity = result.get();
        assertEquals("my-module", identity.getName());
        assertEquals("com.example", identity.getGroupId());
        assertEquals("2.0.0", identity.getVersion());
    }

    @Test
    void testDetect_MissingArtifactId() throws IOException {
        // Given: A pom.xml without artifactId
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <groupId>com.example</groupId>
                <version>1.0.0</version>
            </project>
            """;

        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertFalse(result.isPresent(), "Should not detect without artifactId");
    }

    @Test
    void testDetect_InvalidXml() throws IOException {
        // Given: Invalid XML
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, "not valid xml");

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertFalse(result.isPresent(), "Should handle invalid XML gracefully");
    }

    @Test
    void testGetPriority() {
        // Priority should be higher than generic detectors
        assertTrue(detector.getPriority() > 0);
    }
}
