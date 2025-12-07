package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a detected code project.
 * A code project is a folder containing source code that should be treated as a unit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeProjectDto {

    /**
     * Unique ID for this code project
     */
    private UUID id;

    /**
     * Source ID this project belongs to
     */
    private UUID sourceId;

    /**
     * Root path where this project was found
     */
    private String rootPath;

    /**
     * Project identity information
     */
    private ProjectIdentityDto identity;

    /**
     * Hash of all source file hashes (content hash)
     * Used for duplicate detection
     */
    private String contentHash;

    /**
     * Number of source files (excluding build artifacts, dependencies)
     */
    private Integer sourceFileCount;

    /**
     * Total number of files (including artifacts, dependencies)
     */
    private Integer totalFileCount;

    /**
     * Total size in bytes
     */
    private Long totalSizeBytes;

    /**
     * When this project was scanned
     */
    private Instant scannedAt;

    /**
     * Target archive path (if assigned)
     * e.g., "/Archive/Code/Java/com/example/my-api/1.0.0"
     */
    private String archivePath;
}
