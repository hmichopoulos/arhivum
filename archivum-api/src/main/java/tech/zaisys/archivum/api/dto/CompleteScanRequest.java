package tech.zaisys.archivum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to mark a scan as complete.
 * Sent by scanner when all files have been processed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteScanRequest {

    /**
     * Total number of files discovered and scanned.
     */
    private Long totalFiles;

    /**
     * Total size of all files in bytes.
     */
    private Long totalSize;

    /**
     * Whether the scan completed successfully.
     */
    private Boolean success;
}
