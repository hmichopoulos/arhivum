package tech.zaisys.archivum.server.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Result of batch file ingestion.
 * Contains success/failure counts and detailed error information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileBatchResult {

    private Integer batchNumber;
    private Integer totalFiles;
    private Integer successCount = 0;
    private Integer failureCount = 0;
    private List<UUID> successfulFileIds = new ArrayList<>();
    private List<FileError> errors = new ArrayList<>();

    /**
     * Increment the success counter.
     */
    public void incrementSuccessCount() {
        this.successCount++;
    }

    /**
     * Increment the failure counter.
     */
    public void incrementFailureCount() {
        this.failureCount++;
    }

    /**
     * Details of a file that failed to ingest.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileError {
        private String path;
        private String error;
    }
}
