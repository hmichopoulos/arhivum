package tech.zaisys.archivum.server.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.zaisys.archivum.server.domain.Destination;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Destination with real-time disk space information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DestinationDto {

    private UUID id;
    private String name;
    private Destination.DestinationType destinationType;
    private String mountedPath;
    private String physicalIdentifier;
    private String description;
    private Boolean isActive;
    private Long totalCapacityBytes;
    private Integer priority;
    private Instant createdAt;
    private Instant updatedAt;

    // Real-time filesystem information (not stored in DB)
    private Long availableSpaceBytes;
    private Long usedSpaceBytes;
    private Double usagePercentage;
    private Boolean isAccessible;
    private String errorMessage;
}
