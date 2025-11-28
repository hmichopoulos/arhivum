package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GPS coordinates extracted from EXIF data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GpsCoordinates {

    /**
     * Latitude in decimal degrees
     */
    private Double latitude;

    /**
     * Longitude in decimal degrees
     */
    private Double longitude;

    /**
     * Altitude in meters
     */
    private Double altitude;
}
