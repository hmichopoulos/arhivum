package tech.zaisys.archivum.scanner.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class for extracting Git repository information.
 * Shared across all project detectors to populate Git metadata.
 */
@Slf4j
public final class GitInfoExtractor {

    private static final String GIT_DIR = ".git";

    private GitInfoExtractor() {
        // Utility class, prevent instantiation
    }

    /**
     * Check if the given folder contains a Git repository
     */
    public static boolean hasGitRepository(Path folder) {
        return Files.isDirectory(folder.resolve(GIT_DIR));
    }

    /**
     * Extract all Git information from a folder
     * @return GitInfo object or empty if not a Git repository
     */
    public static Optional<GitInfo> extractGitInfo(Path folder) {
        if (!hasGitRepository(folder)) {
            return Optional.empty();
        }

        try {
            String remote = getGitRemote(folder).orElse(null);
            String branch = getGitBranch(folder).orElse(null);
            String commit = getGitCommit(folder).orElse(null);

            // Only return if we got at least one piece of Git info
            if (remote != null || branch != null || commit != null) {
                return Optional.of(new GitInfo(remote, branch, commit));
            }

            return Optional.empty();

        } catch (Exception e) {
            log.debug("Failed to extract Git info from {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get Git remote URL
     */
    private static Optional<String> getGitRemote(Path folder) {
        return executeGitCommand(folder, "git", "config", "--get", "remote.origin.url");
    }

    /**
     * Get current Git branch
     */
    private static Optional<String> getGitBranch(Path folder) {
        return executeGitCommand(folder, "git", "rev-parse", "--abbrev-ref", "HEAD");
    }

    /**
     * Get current Git commit (short SHA)
     */
    private static Optional<String> getGitCommit(Path folder) {
        return executeGitCommand(folder, "git", "rev-parse", "--short", "HEAD");
    }

    /**
     * Execute a git command and return output
     */
    private static Optional<String> executeGitCommand(Path folder, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(folder.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return Optional.of(line.trim());
                }
            }

            process.waitFor();
            return Optional.empty();

        } catch (IOException | InterruptedException e) {
            log.debug("Git command failed in {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Container for Git repository information
     */
    @Data
    public static class GitInfo {
        private final String remote;
        private final String branch;
        private final String commit;
    }
}
