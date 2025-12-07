package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for code project duplicate group members.
 */
@Entity
@Table(name = "code_project_duplicate_member")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeProjectDuplicateMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_group_id", nullable = false)
    private CodeProjectDuplicateGroup duplicateGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_project_id", nullable = false)
    private CodeProject codeProject;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
