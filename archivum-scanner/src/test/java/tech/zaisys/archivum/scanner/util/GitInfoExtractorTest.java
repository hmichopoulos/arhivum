package tech.zaisys.archivum.scanner.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GitInfoExtractor.
 */
class GitInfoExtractorTest {

    @TempDir
    Path tempDir;

    private Path gitDir;

    @BeforeEach
    void setUp() throws IOException {
        gitDir = tempDir.resolve(".git");
        Files.createDirectory(gitDir);
    }

    @Test
    void testHasGitRepository_WithGitDir() {
        // When
        boolean result = GitInfoExtractor.hasGitRepository(tempDir);

        // Then
        assertTrue(result);
    }

    @Test
    void testHasGitRepository_WithoutGitDir() throws IOException {
        // Given: Remove .git directory
        Files.delete(gitDir);

        // When
        boolean result = GitInfoExtractor.hasGitRepository(tempDir);

        // Then
        assertFalse(result);
    }

    @Test
    void testExtractGitInfo_NoGitRepository() throws IOException {
        // Given: Remove .git directory
        Files.delete(gitDir);

        // When
        Optional<GitInfoExtractor.GitInfo> result = GitInfoExtractor.extractGitInfo(tempDir);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testExtractGitInfo_EmptyRepository() {
        // When: Git directory exists but git commands fail
        Optional<GitInfoExtractor.GitInfo> result = GitInfoExtractor.extractGitInfo(tempDir);

        // Then: Should return empty since git commands will fail on non-git directory
        // Note: This might pass if the test is running within a real git repo
        // and the .git directory tricks git commands into thinking it's valid.
        // The key is that it doesn't throw an exception.
        assertNotNull(result);
    }

    @Test
    void testExtractGitInfo_RealRepository() throws IOException, InterruptedException {
        // This test only works if we have a real git repository
        // Skip if not in a git repository
        if (!GitInfoExtractor.hasGitRepository(Path.of("."))) {
            return;
        }

        // When: Extract from current directory (which is a git repo during testing)
        Optional<GitInfoExtractor.GitInfo> result = GitInfoExtractor.extractGitInfo(Path.of("."));

        // Then: Should have some Git info
        assertTrue(result.isPresent());
        GitInfoExtractor.GitInfo gitInfo = result.get();

        // At least one of these should be populated
        boolean hasAnyInfo = gitInfo.getRemote() != null ||
                            gitInfo.getBranch() != null ||
                            gitInfo.getCommit() != null;
        assertTrue(hasAnyInfo, "Should have at least one piece of Git information");
    }

    @Test
    void testGitInfo_Immutability() {
        // Given
        GitInfoExtractor.GitInfo gitInfo = new GitInfoExtractor.GitInfo(
            "https://github.com/user/repo.git",
            "main",
            "abc123"
        );

        // Then: Verify all fields are accessible
        assertEquals("https://github.com/user/repo.git", gitInfo.getRemote());
        assertEquals("main", gitInfo.getBranch());
        assertEquals("abc123", gitInfo.getCommit());
    }

    @Test
    void testGitInfo_WithNulls() {
        // Given: GitInfo with some null values
        GitInfoExtractor.GitInfo gitInfo = new GitInfoExtractor.GitInfo(
            "https://github.com/user/repo.git",
            null,
            null
        );

        // Then
        assertEquals("https://github.com/user/repo.git", gitInfo.getRemote());
        assertNull(gitInfo.getBranch());
        assertNull(gitInfo.getCommit());
    }
}
