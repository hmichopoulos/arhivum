package tech.zaisys.archivum.api.enums;

/**
 * Type of file migration operation.
 */
public enum MigrationType {
    /**
     * Copy from source disk to NAS.
     */
    NAS_COPY,

    /**
     * Copy from source disk to warehouse disk.
     */
    WAREHOUSE_COPY,

    /**
     * Move from NAS to warehouse disk.
     */
    NAS_TO_WAREHOUSE,

    /**
     * Move from warehouse disk to NAS.
     */
    WAREHOUSE_TO_NAS
}
