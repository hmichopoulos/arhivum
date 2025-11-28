package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Physical identification metadata for storage devices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhysicalId {

    /**
     * Disk UUID (from blkid or similar)
     */
    private String diskUuid;

    /**
     * Partition UUID
     */
    private String partitionUuid;

    /**
     * Volume label (filesystem label)
     */
    private String volumeLabel;

    /**
     * Device serial number
     */
    private String serialNumber;

    /**
     * Mount point at scan time
     */
    private String mountPoint;

    /**
     * Filesystem type (ext4, ntfs, etc.)
     */
    private String filesystemType;

    /**
     * Total capacity in bytes
     */
    private Long capacity;

    /**
     * Used space in bytes
     */
    private Long usedSpace;

    /**
     * Physical sticker label (user-provided)
     */
    private String physicalLabel;

    /**
     * User notes about this device
     */
    private String notes;
}
