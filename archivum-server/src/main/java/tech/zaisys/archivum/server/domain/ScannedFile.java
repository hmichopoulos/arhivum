package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.zaisys.archivum.api.dto.ExifMetadata;
import tech.zaisys.archivum.api.enums.FileState;
import tech.zaisys.archivum.api.enums.FileStatus;
import tech.zaisys.archivum.api.enums.Zone;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for scanned files with metadata and hashes.
 * Stores files discovered during directory scanning.
 */
@Entity
@Table(name = "scanned_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannedFile {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    // Original location
    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String extension;

    // File properties
    @Column(nullable = false)
    private Long size;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "file_created_at")
    private Instant fileCreatedAt;

    @Column(name = "accessed_at")
    private Instant accessedAt;

    // Content metadata
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exif_metadata", columnDefinition = "jsonb")
    private ExifMetadata exifMetadata;

    // Processing state
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private FileStatus status = FileStatus.HASHED;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private Zone zone = Zone.UNKNOWN;

    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private Boolean isDuplicate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_file_id")
    private ScannedFile originalFile;

    // Migration workflow fields

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20)
    @Builder.Default
    private FileState state = FileState.DISCOVERED;

    @Column(name = "ignore_for_migration", nullable = false)
    @Builder.Default
    private Boolean ignoreForMigration = false;

    @Column(name = "migrated_path", length = 1000)
    private String migratedPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of")
    private ScannedFile duplicateOf;

    // Timestamps
    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

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
