package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @NotNull(message = "Source ID is required")
    private UUID sourceId;

    /**
     * Root path where this project was found
     */
    @NotBlank(message = "Root path is required")
    private String rootPath;

    /**
     * Project identity information
     */
    @NotNull(message = "Project identity is required")
    @Valid
    private ProjectIdentityDto identity;

    /**
     * Hash of all source file hashes (content hash)
     * Used for duplicate detection
     */
    @NotBlank(message = "Content hash is required")
    @Pattern(regexp = "^[a-f0-9]{64}$", message = "Content hash must be a valid SHA-256 hash (64 hex characters)")
    private String contentHash;

    /**
     * Number of source files (excluding build artifacts, dependencies)
     */
    @NotNull(message = "Source file count is required")
    @Min(value = 0, message = "Source file count must be non-negative")
    private Integer sourceFileCount;

    /**
     * Total number of files (including artifacts, dependencies)
     */
    @NotNull(message = "Total file count is required")
    @Min(value = 0, message = "Total file count must be non-negative")
    private Integer totalFileCount;

    /**
     * Total size in bytes
     */
    @NotNull(message = "Total size is required")
    @Min(value = 0, message = "Total size must be non-negative")
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
