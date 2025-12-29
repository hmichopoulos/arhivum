package tech.zaisys.archivum.api.enums;

/**
 * Status of an individual migration task.
 */
public enum MigrationTaskStatus {
    /**
     * Task not yet started.
     */
    PENDING,

    /**
     * Task waiting for source disk to be connected.
     */
    WAITING_FOR_DISK,

    /**
     * Task is currently copying file.
     */
    IN_PROGRESS,

    /**
     * Task completed successfully.
     */
    COMPLETE,

    /**
     * Task failed with error.
     */
    FAILED
}
