package tech.zaisys.archivum.api.enums;

/**
 * Type of source being scanned.
 */
public enum SourceType {
    /**
     * Physical disk
     */
    DISK,

    /**
     * Partition on a disk
     */
    PARTITION,

    /**
     * LVM logical volume
     */
    LVM_VOLUME,

    /**
     * TAR archive
     */
    ARCHIVE_TAR,

    /**
     * ZIP archive
     */
    ARCHIVE_ZIP,

    /**
     * 7-Zip archive
     */
    ARCHIVE_7Z,

    /**
     * Disk image (ISO, IMG, etc.)
     */
    ARCHIVE_IMG,

    /**
     * Acronis backup archive
     */
    ARCHIVE_ACRONIS,

    /**
     * Other archive format
     */
    ARCHIVE_OTHER,

    /**
     * Google Drive
     */
    CLOUD_GDRIVE,

    /**
     * Microsoft OneDrive
     */
    CLOUD_ONEDRIVE,

    /**
     * Dropbox
     */
    CLOUD_DROPBOX,

    /**
     * Remote SSH server
     */
    SSH_SERVER
}
