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
 * Tests for CodeProjectDetectionService.
 */
class CodeProjectDetectionServiceTest {

    private CodeProjectDetectionService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new CodeProjectDetectionService();
    }

    @Test
    void testDetectProject_Maven() throws IOException {
        // Given: A Maven project
        String pomContent = """
            <?xml version="1.0"?>
            <project>
                <groupId>com.test</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
            </project>
            """;

        Files.writeString(tempDir.resolve("pom.xml"), pomContent);

        // When
        Optional<ProjectIdentityDto> result = service.detectProject(tempDir);

        // Then
        assertTrue(result.isPresent());
        assertEquals(ProjectType.MAVEN, result.get().getType());
    }

    @Test
    void testDetectProject_Npm() throws IOException {
        // Given: An NPM project
        String packageJson = """
            {
                "name": "test-package",
                "version": "1.0.0"
            }
            """;

        Files.writeString(tempDir.resolve("package.json"), packageJson);

        // When
        Optional<ProjectIdentityDto> result = service.detectProject(tempDir);

        // Then
        assertTrue(result.isPresent());
        assertEquals(ProjectType.NPM, result.get().getType());
    }

    @Test
    void testDetectProject_NoProject() {
        // Given: An empty directory
        // When
        Optional<ProjectIdentityDto> result = service.detectProject(tempDir);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testDetectProject_PriorityOrder() throws IOException {
        // Given: Directory with both pom.xml and package.json
        // Maven detector has higher priority, so it should be detected as Maven
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project>
                <groupId>test</groupId>
                <artifactId>test</artifactId>
                <version>1.0.0</version>
            </project>
            """);

        Files.writeString(tempDir.resolve("package.json"), """
            {
                "name": "test",
                "version": "1.0.0"
            }
            """);

        // When
        Optional<ProjectIdentityDto> result = service.detectProject(tempDir);

        // Then
        assertTrue(result.isPresent());
        // Should detect as Maven (higher priority)
        assertEquals(ProjectType.MAVEN, result.get().getType());
    }

    @Test
    void testIsCodeProject_True() throws IOException {
        // Given: A Maven project
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        // When
        boolean result = service.isCodeProject(tempDir);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsCodeProject_False() {
        // Given: Empty directory
        // When
        boolean result = service.isCodeProject(tempDir);

        // Then
        assertFalse(result);
    }
}
