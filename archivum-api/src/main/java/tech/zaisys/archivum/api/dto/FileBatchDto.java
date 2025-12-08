package tech.zaisys.archivum.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    @NotNull(message = "Source ID is required")
    private UUID sourceId;

    /**
     * List of files in this batch
     */
    @NotNull(message = "Files list cannot be null")
    @NotEmpty(message = "Files list cannot be empty")
    @Valid
    private List<FileDto> files;

    /**
     * Batch sequence number (for ordering)
     */
    @NotNull(message = "Batch number is required")
    @Positive(message = "Batch number must be positive")
    private Integer batchNumber;
}
