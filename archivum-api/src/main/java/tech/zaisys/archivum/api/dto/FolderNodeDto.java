package tech.zaisys.archivum.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing a node in the filesystem tree.
 * Can represent either a folder or a file.
 */
@Value
@Builder
public class FolderNodeDto {

    /**
     * Node name (folder or file name).
     */
    String name;

    /**
     * Full path from source root.
     */
    String path;

    /**
     * Node type: FOLDER or FILE.
     */
    NodeType type;

    /**
     * File ID (only for FILE type).
     */
    UUID fileId;

    /**
     * File size in bytes (only for FILE type).
     */
    Long size;

    /**
     * File extension (only for FILE type).
     */
    String extension;

    /**
     * Whether file is a duplicate (only for FILE type).
     */
    Boolean isDuplicate;

    /**
     * Child nodes (only for FOLDER type).
     * Empty list for files.
     */
    List<FolderNodeDto> children;

    /**
     * Number of files in this folder (recursive, only for FOLDER type).
     */
    Integer fileCount;

    /**
     * Total size of all files in this folder (recursive, only for FOLDER type).
     */
    Long totalSize;

    public enum NodeType {
        FOLDER,
        FILE
    }
}
