package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for code project duplicate groups.
 */
@Entity
@Table(name = "code_project_duplicate_group")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeProjectDuplicateGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_type", nullable = false, length = 50)
    private DuplicateType duplicateType;

    @Column(name = "similarity_percent", precision = 5, scale = 2)
    private BigDecimal similarityPercent;

    @Column(name = "files_only_in_primary")
    private Integer filesOnlyInPrimary;

    @Column(name = "files_only_in_secondary")
    private Integer filesOnlyInSecondary;

    @Column(name = "files_in_both")
    private Integer filesInBoth;

    @Enumerated(EnumType.STRING)
    @Column(name = "diff_complexity", length = 20)
    private DiffComplexity diffComplexity;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 50)
    @Builder.Default
    private ResolutionStatus resolutionStatus = ResolutionStatus.PENDING;

    @OneToMany(mappedBy = "duplicateGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CodeProjectDuplicateMember> members = new ArrayList<>();

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

    public enum DuplicateType {
        EXACT,
        SAME_PROJECT_DIFF_CONTENT,
        DIFFERENT_VERSION
    }

    public enum DiffComplexity {
        TRIVIAL,
        SIMPLE,
        MEDIUM,
        COMPLEX
    }

    public enum ResolutionStatus {
        PENDING,
        KEEP_BOTH,
        KEEP_PRIMARY,
        MERGED
    }
}
