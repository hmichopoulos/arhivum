package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a source (disk, partition, archive, etc.) being scanned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceDto {

    /**
     * Unique source ID
     */
    private UUID id;

    /**
     * User-provided logical name
     */
    private String name;

    /**
     * Type of source
     */
    private SourceType type;

    /**
     * Root path that was scanned
     */
    private String rootPath;

    /**
     * Physical identification metadata
     */
    private PhysicalId physicalId;

    /**
     * Parent source ID (for hierarchical relationships)
     */
    private UUID parentSourceId;

    /**
     * Child source IDs
     */
    private List<UUID> childSourceIds;

    /**
     * Current scan status
     */
    private ScanStatus status;

    /**
     * Whether this source is postponed (cataloged but not scanned)
     */
    private Boolean postponed;

    /**
     * Total number of files found
     */
    private Long totalFiles;

    /**
     * Total size in bytes
     */
    private Long totalSize;

    /**
     * Number of files processed so far
     */
    private Long processedFiles;

    /**
     * Number of bytes processed so far
     */
    private Long processedSize;

    /**
     * When the scan started
     */
    private Instant scanStartedAt;

    /**
     * When the scan completed
     */
    private Instant scanCompletedAt;

    /**
     * When this record was created
     */
    private Instant createdAt;

    /**
     * User notes about this source
     */
    private String notes;
}
