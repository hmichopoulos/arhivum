package tech.zaisys.archivum.api.enums;

/**
 * Classification zones for files and folders.
 * Determines deduplication and archiving behavior.
 */
public enum Zone {
    /**
     * Photos, videos, music
     * File dedup: YES, Folder dedup: YES
     */
    MEDIA,

    /**
     * PDFs, Office documents
     * File dedup: YES, Folder dedup: YES
     */
    DOCUMENTS,

    /**
     * Ebooks, audiobooks
     * File dedup: YES, Folder dedup: YES
     */
    BOOKS,

    /**
     * Installers, applications, games
     * File dedup: NO (preserve DLLs), Folder dedup: YES
     */
    SOFTWARE,

    /**
     * Full backup archives
     * File dedup: NO, Folder dedup: YES
     */
    BACKUP,

    /**
     * Source code repositories
     * File dedup: NO, Folder dedup: YES
     */
    CODE,

    /**
     * Software libraries (Maven .m2, Python .venv, node_modules, etc.)
     * File dedup: YES, Folder dedup: YES
     * Note: Usually can be ignored/redownloaded from internet
     */
    SOFTWARE_LIB,

    /**
     * Unclassified content needing manual review
     * File dedup: NO, Folder dedup: NO
     */
    UNKNOWN
}
