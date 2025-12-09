package tech.zaisys.archivum.server.domain;

import jakarta.persistence.*;
import lombok.*;
import tech.zaisys.archivum.api.enums.Zone;

import java.util.UUID;

/**
 * Entity representing explicit zone classification for a folder.
 * Folders without explicit zones inherit from their parent folder.
 */
@Entity
@Table(
    name = "folder_zone",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "folder_path"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    /**
     * Full folder path from source root (e.g., "/Documents/Work").
     */
    @Column(name = "folder_path", nullable = false, length = 1000)
    private String folderPath;

    /**
     * Explicit zone classification for this folder.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Zone zone;
}
