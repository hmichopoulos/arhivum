package tech.zaisys.archivum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Batch of files to send to server.
 * Scanner batches files (e.g., every 1000) to reduce API calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileBatchDto {

    /**
     * Source ID these files belong to
     */
    private UUID sourceId;

    /**
     * List of files in this batch
     */
    private List<FileDto> files;

    /**
     * Batch sequence number (for ordering)
     */
    private Integer batchNumber;
}
