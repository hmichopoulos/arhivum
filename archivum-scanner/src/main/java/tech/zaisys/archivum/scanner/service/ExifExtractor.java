package tech.zaisys.archivum.scanner.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ExifMetadata;
import tech.zaisys.archivum.api.dto.GpsCoordinates;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

/**
 * Service for extracting EXIF metadata from image files.
 * Uses Drew Noakes' metadata-extractor library.
 */
@Slf4j
public class ExifExtractor {

    /**
     * Extract EXIF metadata from an image file.
     * Returns null if file has no EXIF data or extraction fails.
     *
     * @param file Path to image file
     * @return ExifMetadata or null
     */
    public ExifMetadata extractExif(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            ExifSubIFDDirectory exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            // Return null if no EXIF directories found
            if (ifd0 == null && exifSubIFD == null && gpsDir == null) {
                log.debug("No EXIF data found in: {}", file);
                return null;
            }

            return ExifMetadata.builder()
                .cameraMake(getCameraMake(ifd0))
                .cameraModel(getCameraModel(ifd0))
                .dateTimeOriginal(getDateTimeOriginal(exifSubIFD))
                .width(getWidth(exifSubIFD))
                .height(getHeight(exifSubIFD))
                .orientation(getOrientation(ifd0))
                .gps(extractGps(gpsDir))
                .lensModel(getLensModel(exifSubIFD))
                .focalLength(getFocalLength(exifSubIFD))
                .aperture(getAperture(exifSubIFD))
                .shutterSpeed(getShutterSpeed(exifSubIFD))
                .iso(getIso(exifSubIFD))
                .flash(getFlash(exifSubIFD))
                .build();

        } catch (ImageProcessingException e) {
            log.warn("Cannot process image metadata for: {} - {}", file, e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("Cannot read file for EXIF: {} - {}", file, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error extracting EXIF from: {}", file, e);
            return null;
        }
    }

    private String getCameraMake(ExifIFD0Directory ifd0) {
        return ifd0 != null ? ifd0.getString(ExifIFD0Directory.TAG_MAKE) : null;
    }

    private String getCameraModel(ExifIFD0Directory ifd0) {
        return ifd0 != null ? ifd0.getString(ExifIFD0Directory.TAG_MODEL) : null;
    }

    private Instant getDateTimeOriginal(ExifSubIFDDirectory exifSubIFD) {
        if (exifSubIFD == null) return null;
        Date date = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        return date != null ? date.toInstant() : null;
    }

    private Integer getWidth(ExifSubIFDDirectory exifSubIFD) {
        return exifSubIFD != null ? exifSubIFD.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH) : null;
    }

    private Integer getHeight(ExifSubIFDDirectory exifSubIFD) {
        return exifSubIFD != null ? exifSubIFD.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT) : null;
    }

    private Integer getOrientation(ExifIFD0Directory ifd0) {
        return ifd0 != null ? ifd0.getInteger(ExifIFD0Directory.TAG_ORIENTATION) : null;
    }

    private GpsCoordinates extractGps(GpsDirectory gpsDir) {
        if (gpsDir == null) return null;

        try {
            var geoLocation = gpsDir.getGeoLocation();
            Double latitude = geoLocation != null ? geoLocation.getLatitude() : null;
            Double longitude = geoLocation != null ? geoLocation.getLongitude() : null;
            Double altitude = gpsDir.getDouble(GpsDirectory.TAG_ALTITUDE);

            if (latitude == null && longitude == null && altitude == null) {
                return null;
            }

            return GpsCoordinates.builder()
                .latitude(latitude)
                .longitude(longitude)
                .altitude(altitude)
                .build();
        } catch (Exception e) {
            log.warn("Error extracting GPS coordinates: {}", e.getMessage());
            return null;
        }
    }

    private String getLensModel(ExifSubIFDDirectory exifSubIFD) {
        return exifSubIFD != null ? exifSubIFD.getString(ExifSubIFDDirectory.TAG_LENS_MODEL) : null;
    }

    private Double getFocalLength(ExifSubIFDDirectory exifSubIFD) {
        return exifSubIFD != null ? exifSubIFD.getDoubleObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH) : null;
    }

    private Double getAperture(ExifSubIFDDirectory exifSubIFD) {
        return exifSubIFD != null ? exifSubIFD.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER) : null;
    }

    private String getShutterSpeed(ExifSubIFDDirectory exifSubIFD) {
        if (exifSubIFD == null) return null;
        return exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_SHUTTER_SPEED);
    }

    private Integer getIso(ExifSubIFDDirectory exifSubIFD) {
        return exifSubIFD != null ? exifSubIFD.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT) : null;
    }

    private Boolean getFlash(ExifSubIFDDirectory exifSubIFD) {
        if (exifSubIFD == null) return null;
        Integer flashValue = exifSubIFD.getInteger(ExifSubIFDDirectory.TAG_FLASH);
        return flashValue != null ? (flashValue & 0x1) != 0 : null;
    }
}
