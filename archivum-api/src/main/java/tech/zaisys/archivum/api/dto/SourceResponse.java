package tech.zaisys.archivum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response after creating or updating a source.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceResponse {

    /**
     * Source ID
     */
    private UUID id;

    /**
     * Success status
     */
    private Boolean success;

    /**
     * Message
     */
    private String message;
}
