package tech.zaisys.archivum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response indicating which hashes already exist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckHashResponse {

    /**
     * Map of hash -> exists (true if hash already in database)
     */
    private Map<String, Boolean> results;
}
