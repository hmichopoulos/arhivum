package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for migration destinations (mounted filesystem paths).
 */
@Entity
@Table(name = "destination")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, length = 50)
    private DestinationType destinationType;

    @Column(name = "mounted_path", nullable = false, length = 1000)
    private String mountedPath;

    @Column(name = "physical_identifier", length = 500)
    private String physicalIdentifier;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "total_capacity_bytes")
    private Long totalCapacityBytes;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

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

    public enum DestinationType {
        NAS,        // Final archive location
        WAREHOUSE,  // Large files on external HDDs
        STAGING     // Temporary staging area
    }
}
