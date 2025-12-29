package tech.zaisys.archivum.api.enums;

/**
 * Enum representing the scanning purpose of a source.
 * Determines how files from this source should be handled during migration.
 */
public enum SourceScanType {
    /**
     * Discovery scan - Files to be migrated to final destinations.
     * Default mode for scanning messy external disks.
     */
    DISCOVERY,

    /**
     * Destination scan - Already organized files in final location.
     * All files are automatically pinned to prevent re-migration.
     * Used for baseline scanning of already-organized archives.
     */
    DESTINATION,

    /**
     * Warehouse scan - Files to be consolidated to organized warehouse disks.
     * Files are cataloged and can be moved between warehouse disks and NAS.
     */
    WAREHOUSE
}
