package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.api.enums.MigrationTaskStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for individual migration tasks.
 * Represents a single file migration within a plan.
 */
@Entity
@Table(name = "migration_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "destination_path", nullable = false, length = 1000)
    private String destinationPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MigrationTaskStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
