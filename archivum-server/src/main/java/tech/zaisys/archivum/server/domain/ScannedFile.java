package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.zaisys.archivum.api.dto.ExifMetadata;
import tech.zaisys.archivum.api.enums.FileStatus;

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

    @Column(length = 50)
    private String extension;

    // File properties
    @Column(nullable = false)
    private Long size;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;

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

    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private Boolean isDuplicate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_file_id")
    private ScannedFile originalFile;

    // Timestamps
    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "created_at_db", nullable = false, updatable = false)
    private Instant createdAtDb;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAtDb = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
