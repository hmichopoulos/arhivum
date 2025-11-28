package tech.zaisys.archivum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to mark a scan as completed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteScanRequest {

    /**
     * Source ID being completed
     */
    private UUID sourceId;

    /**
     * Total number of files scanned
     */
    private Long totalFiles;

    /**
     * Total size in bytes
     */
    private Long totalSize;

    /**
     * Whether scan completed successfully
     */
    private Boolean success;

    /**
     * Error message if failed
     */
    private String errorMessage;
}
