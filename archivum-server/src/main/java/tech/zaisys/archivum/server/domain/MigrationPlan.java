package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.api.enums.MigrationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for migration plans.
 * Groups multiple file migrations into a single logical operation.
 */
@Entity
@Table(name = "migration_plan")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MigrationStatus status;

    @Column(name = "total_files", nullable = false)
    @Builder.Default
    private Integer totalFiles = 0;

    @Column(name = "total_size_bytes", nullable = false)
    @Builder.Default
    private Long totalSizeBytes = 0L;

    @Column(name = "destination_type", nullable = false, length = 50)
    private String destinationType;

    @Column(name = "destination_base_path", length = 1000)
    private String destinationBasePath;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
