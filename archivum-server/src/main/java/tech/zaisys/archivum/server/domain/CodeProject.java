package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.api.enums.ProjectType;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for code projects detected during scanning.
 */
@Entity
@Table(name = "code_project")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "root_path", nullable = false)
    private String rootPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 50)
    private ProjectType projectType;

    @Column(nullable = false)
    private String name;

    private String version;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "git_remote")
    private String gitRemote;

    @Column(name = "git_branch")
    private String gitBranch;

    @Column(name = "git_commit")
    private String gitCommit;

    @Column(nullable = false)
    private String identifier;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "source_file_count", nullable = false)
    private Integer sourceFileCount;

    @Column(name = "total_file_count", nullable = false)
    private Integer totalFileCount;

    @Column(name = "total_size_bytes", nullable = false)
    private Long totalSizeBytes;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "archive_path")
    private String archivePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
