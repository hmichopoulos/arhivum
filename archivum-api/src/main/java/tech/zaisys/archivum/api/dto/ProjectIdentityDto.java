package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.api.enums.ProjectType;

/**
 * Represents the identity of a code project.
 * This is used to uniquely identify and match projects across different scans.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectIdentityDto {

    /**
     * Project type (Maven, NPM, Go, etc.)
     */
    private ProjectType type;

    /**
     * Project name
     */
    private String name;

    /**
     * Project version (if available)
     */
    private String version;

    /**
     * Group ID (for Maven/Gradle)
     * e.g., "com.example"
     */
    private String groupId;

    /**
     * Git remote URL (for Git repos)
     * e.g., "https://github.com/user/repo.git"
     */
    private String gitRemote;

    /**
     * Git branch (for Git repos)
     */
    private String gitBranch;

    /**
     * Git commit SHA (short form, for Git repos)
     */
    private String gitCommit;

    /**
     * Computed identifier for this project.
     * Format depends on project type:
     * - Maven/Gradle: "groupId:artifactId:version"
     * - NPM: "name:version"
     * - Go: "module-path"
     * - Python/Rust: "name:version"
     * - Generic: "unknown:foldername" or "remote@branch" (for Git repos)
     */
    private String identifier;
}
