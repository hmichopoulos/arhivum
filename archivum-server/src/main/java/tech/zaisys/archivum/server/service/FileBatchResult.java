package tech.zaisys.archivum.server.service;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Result of batch file ingestion.
 * Contains success/failure counts and detailed error information.
 * Immutable result object.
 */
@Value
@Builder
public class FileBatchResult {

    Integer batchNumber;
    Integer totalFiles;
    Integer successCount;
    Integer failureCount;
    List<UUID> successfulFileIds;
    List<FileError> errors;

    /**
     * Details of a file that failed to ingest.
     * Immutable error record.
     */
    @Value
    public static class FileError {
        String path;
        String error;
    }
}
