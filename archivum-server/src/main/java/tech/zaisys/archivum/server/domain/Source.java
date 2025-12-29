package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tech.zaisys.archivum.api.dto.PhysicalId;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceScanType;
import tech.zaisys.archivum.api.enums.SourceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for sources (disks, partitions, archives) being scanned.
 * Supports hierarchical parent-child relationships (e.g., partition belongs to disk).
 */
@Entity
@Table(name = "source")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Source parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Source> children = new ArrayList<>();

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType type;

    @Column(name = "root_path", nullable = false, columnDefinition = "TEXT")
    private String rootPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "physical_id", columnDefinition = "jsonb")
    private PhysicalId physicalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ScanStatus status;

    @Column(nullable = false)
    @Builder.Default
    private Boolean postponed = false;

    @Column(name = "total_files", nullable = false)
    @Builder.Default
    private Long totalFiles = 0L;

    @Column(name = "total_size", nullable = false)
    @Builder.Default
    private Long totalSize = 0L;

    @Column(name = "processed_files", nullable = false)
    @Builder.Default
    private Long processedFiles = 0L;

    @Column(name = "processed_size", nullable = false)
    @Builder.Default
    private Long processedSize = 0L;

    @Column(name = "scan_started_at")
    private Instant scanStartedAt;

    @Column(name = "scan_completed_at")
    private Instant scanCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Disk tracking fields for offline workflow support

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "state", length = 20)
    @Builder.Default
    private String diskState = "OFFLINE"; // ONLINE or OFFLINE

    @Column(name = "mount_point", length = 500)
    private String mountPoint;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    @Builder.Default
    private SourceScanType sourceScanType = SourceScanType.DISCOVERY;

    /**
     * Add a child source to this source.
     * Maintains bidirectional relationship.
     *
     * @param child Child source to add
     */
    public void addChild(Source child) {
        children.add(child);
        child.setParent(this);
    }

    /**
     * Remove a child source from this source.
     * Maintains bidirectional relationship.
     *
     * @param child Child source to remove
     */
    public void removeChild(Source child) {
        children.remove(child);
        child.setParent(null);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
