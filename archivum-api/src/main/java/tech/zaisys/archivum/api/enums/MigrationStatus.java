package tech.zaisys.archivum.api.enums;

/**
 * Status of a migration plan.
 */
public enum MigrationStatus {
    /**
     * Plan is being created or modified.
     */
    DRAFT,

    /**
     * Plan is ready to be executed.
     */
    READY,

    /**
     * Plan is currently executing.
     */
    EXECUTING,

    /**
     * Plan completed successfully.
     */
    COMPLETE,

    /**
     * Plan failed with errors.
     */
    FAILED
}
