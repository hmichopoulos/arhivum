package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.api.enums.MigrationType;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for file migration history.
 * Audit trail of all file migrations.
 */
@Entity
@Table(name = "file_migration_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMigrationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "from_location", nullable = false, length = 500)
    private String fromLocation;

    @Column(name = "to_location", nullable = false, length = 500)
    private String toLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "migration_type", nullable = false, length = 50)
    private MigrationType migrationType;

    @Column(name = "migrated_at", nullable = false)
    private Instant migratedAt;

    @Column(name = "migrated_by", length = 100)
    private String migratedBy;

    @PrePersist
    protected void onCreate() {
        if (migratedAt == null) {
            migratedAt = Instant.now();
        }
    }
}
