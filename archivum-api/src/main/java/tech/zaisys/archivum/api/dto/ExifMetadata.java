package tech.zaisys.archivum.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * EXIF metadata extracted from image files.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExifMetadata {

    /**
     * Camera make (e.g., "Canon", "Apple")
     */
    private String cameraMake;

    /**
     * Camera model (e.g., "iPhone 13 Pro", "EOS 5D Mark IV")
     */
    private String cameraModel;

    /**
     * Date/time original photo was taken
     */
    private Instant dateTimeOriginal;

    /**
     * Image width in pixels
     */
    private Integer width;

    /**
     * Image height in pixels
     */
    private Integer height;

    /**
     * Orientation (rotation) value
     */
    private Integer orientation;

    /**
     * GPS coordinates if available
     */
    private GpsCoordinates gps;

    /**
     * Lens model
     */
    private String lensModel;

    /**
     * Focal length in mm
     */
    private Double focalLength;

    /**
     * Aperture (f-number)
     */
    private Double aperture;

    /**
     * Shutter speed in seconds
     */
    private String shutterSpeed;

    /**
     * ISO speed
     */
    private Integer iso;

    /**
     * Flash fired (true/false)
     */
    private Boolean flash;
}
