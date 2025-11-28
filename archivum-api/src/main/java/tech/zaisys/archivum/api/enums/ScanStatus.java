package tech.zaisys.archivum.api.enums;

/**
 * Status of a source scan.
 */
public enum ScanStatus {
    /**
     * Scan has been registered but not started
     */
    PENDING,

    /**
     * Scan is currently in progress
     */
    SCANNING,

    /**
     * Source cataloged but contents not scanned (for archives)
     */
    POSTPONED,

    /**
     * Scan completed successfully
     */
    COMPLETED,

    /**
     * Scan failed with errors
     */
    FAILED,

    /**
     * Scan paused by user
     */
    PAUSED
}
