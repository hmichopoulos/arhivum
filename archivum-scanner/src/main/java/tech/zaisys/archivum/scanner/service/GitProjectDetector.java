package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Git repositories by looking for .git directory.
 * Extracts remote URL, current branch, and commit.
 */
@Slf4j
public class GitProjectDetector implements ProjectDetector {

    private static final String MARKER_DIR = ".git";
    private static final Pattern REMOTE_PATTERN = Pattern.compile("https?://([^/]+)/(.+?)(?:\\.git)?$|git@([^:]+):(.+?)(?:\\.git)?$");

    @Override
    public boolean canDetect(Path folder) {
        return Files.isDirectory(folder.resolve(MARKER_DIR));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        Path gitDir = folder.resolve(MARKER_DIR);
        if (!Files.isDirectory(gitDir)) {
            return Optional.empty();
        }

        try {
            String remote = getGitRemote(folder).orElse("unknown");
            String branch = getGitBranch(folder).orElse("main");
            String commit = getGitCommit(folder).orElse(null);

            // Extract repository name from remote URL
            String repoName = extractRepoName(remote);
            String identifier = remote + "@" + branch;

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.GENERIC)
                .name(repoName)
                .gitRemote(remote)
                .gitBranch(branch)
                .gitCommit(commit)
                .identifier(identifier)
                .build();

            log.debug("Detected Git repository: {}", identifier);
            return Optional.of(identity);

        } catch (Exception e) {
            log.warn("Failed to extract Git info from {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPriority() {
        return 5; // Lower than build tool detectors (Maven/Gradle/npm might also have .git)
    }

    /**
     * Get Git remote URL
     */
    private Optional<String> getGitRemote(Path folder) {
        return executeGitCommand(folder, "git", "config", "--get", "remote.origin.url");
    }

    /**
     * Get current Git branch
     */
    private Optional<String> getGitBranch(Path folder) {
        return executeGitCommand(folder, "git", "rev-parse", "--abbrev-ref", "HEAD");
    }

    /**
     * Get current Git commit (short SHA)
     */
    private Optional<String> getGitCommit(Path folder) {
        return executeGitCommand(folder, "git", "rev-parse", "--short", "HEAD");
    }

    /**
     * Execute a git command and return output
     */
    private Optional<String> executeGitCommand(Path folder, String... command) {
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
     * Extract repository name from remote URL
     */
    private String extractRepoName(String remote) {
        if (remote.equals("unknown")) {
            return "unknown";
        }

        Matcher matcher = REMOTE_PATTERN.matcher(remote);
        if (matcher.find()) {
            // HTTPS: group 2, SSH: group 4
            String path = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            if (path != null) {
                // Extract last part (repo name)
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash != -1) {
                    return path.substring(lastSlash + 1);
                }
                return path;
            }
        }

        // Fallback: use the whole remote as name
        return remote;
    }
}
