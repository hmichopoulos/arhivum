package tech.zaisys.archivum.api.enums;

/**
 * Enum representing the state of a file in the migration workflow.
 * Tracks file lifecycle from discovery through migration to final state.
 */
public enum FileState {
    /**
     * File found during scan (default state).
     * Ready for classification and planning.
     */
    DISCOVERED,

    /**
     * File is already in final location (don't migrate).
     * Used for DESTINATION source scans to mark already-organized files.
     */
    PINNED,

    /**
     * File is ready to be migrated.
     * Migration plan has been approved, waiting for execution.
     */
    STAGED,

    /**
     * File is currently being migrated.
     * Transfer in progress.
     */
    MIGRATING,

    /**
     * File has been successfully migrated.
     * Located at migratedPath.
     */
    MIGRATED,

    /**
     * File is on a warehouse disk.
     * Cataloged but stored on external disk.
     */
    WAREHOUSED,

    /**
     * File has been marked for deletion.
     * Waiting for cleanup period to expire.
     */
    DELETED
}
