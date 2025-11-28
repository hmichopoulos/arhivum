package tech.zaisys.archivum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to check if hashes already exist in the system.
 * Used to optimize metadata extraction - skip files with known hashes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckHashRequest {

    /**
     * List of SHA-256 hashes to check
     */
    private List<String> hashes;
}
