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
 * Tests for NPM project detection.
 */
class NpmProjectDetectorTest {

    private NpmProjectDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new NpmProjectDetector();
    }

    @Test
    void testCanDetect_WithPackageJson() throws IOException {
        // Given
        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, "{}");

        // When
        boolean result = detector.canDetect(tempDir);

        // Then
        assertTrue(result);
    }

    @Test
    void testCanDetect_WithoutPackageJson() {
        // When
        boolean result = detector.canDetect(tempDir);

        // Then
        assertFalse(result);
    }

    @Test
    void testDetect_ValidPackageJson() throws IOException {
        // Given
        String packageJsonContent = """
            {
                "name": "my-package",
                "version": "1.2.3",
                "description": "A test package"
            }
            """;

        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJsonContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertTrue(result.isPresent());
        ProjectIdentityDto identity = result.get();
        assertEquals(ProjectType.NPM, identity.getType());
        assertEquals("my-package", identity.getName());
        assertEquals("1.2.3", identity.getVersion());
        assertEquals("my-package:1.2.3", identity.getIdentifier());
    }

    @Test
    void testDetect_ScopedPackage() throws IOException {
        // Given: Scoped package name
        String packageJsonContent = """
            {
                "name": "@myorg/my-package",
                "version": "2.0.0"
            }
            """;

        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJsonContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertTrue(result.isPresent());
        ProjectIdentityDto identity = result.get();
        assertEquals("@myorg/my-package", identity.getName());
        assertEquals("@myorg/my-package:2.0.0", identity.getIdentifier());
    }

    @Test
    void testDetect_MissingName() throws IOException {
        // Given: package.json without name
        String packageJsonContent = """
            {
                "version": "1.0.0"
            }
            """;

        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJsonContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testDetect_MissingVersion() throws IOException {
        // Given: package.json without version (should use "unknown")
        String packageJsonContent = """
            {
                "name": "my-package"
            }
            """;

        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJsonContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then
        assertTrue(result.isPresent());
        assertEquals("unknown", result.get().getVersion());
    }

    @Test
    void testDetect_WithoutGitRepository() throws IOException {
        // Given: Valid package.json but no .git directory
        String packageJsonContent = """
            {
                "name": "my-package",
                "version": "1.0.0"
            }
            """;

        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJsonContent);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then: Should detect project but without Git info
        assertTrue(result.isPresent());
        ProjectIdentityDto identity = result.get();
        assertNull(identity.getGitRemote());
        assertNull(identity.getGitBranch());
        assertNull(identity.getGitCommit());
    }

    @Test
    void testDetect_WithGitRepository() throws IOException {
        // Given: Valid package.json and .git directory
        String packageJsonContent = """
            {
                "name": "my-package",
                "version": "1.0.0"
            }
            """;

        Path packageJson = tempDir.resolve("package.json");
        Files.writeString(packageJson, packageJsonContent);

        // Create .git directory to simulate Git repository
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);

        // When
        Optional<ProjectIdentityDto> result = detector.detect(tempDir);

        // Then: Should detect project
        // Note: Git info extraction may fail in test environment without actual git repo
        // but the detector should still work
        assertTrue(result.isPresent());
        ProjectIdentityDto identity = result.get();
        assertEquals("my-package", identity.getName());
        assertEquals("1.0.0", identity.getVersion());
        // Git fields may be null if git commands fail, which is OK in test environment
    }
}
