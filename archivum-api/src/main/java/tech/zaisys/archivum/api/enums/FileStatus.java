package tech.zaisys.archivum.api.enums;

/**
 * Status of a scanned file.
 */
public enum FileStatus {
    /**
     * File discovered but not yet hashed
     */
    DISCOVERED,

    /**
     * Hash computed
     */
    HASHED,

    /**
     * File analyzed for duplicates
     */
    ANALYZED,

    /**
     * File classified into target category
     */
    CLASSIFIED,

    /**
     * File copied to staging area
     */
    STAGED,

    /**
     * File migrated to final archive location
     */
    MIGRATED,

    /**
     * File is a duplicate of another file
     */
    DUPLICATE
}
