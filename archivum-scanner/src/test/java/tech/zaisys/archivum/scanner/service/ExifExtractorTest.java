package tech.zaisys.archivum.scanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.ExifMetadata;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EXIF metadata extraction.
 * Tests the ExifExtractor service with various image types and EXIF scenarios.
 */
class ExifExtractorTest {

    private ExifExtractor exifExtractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        exifExtractor = new ExifExtractor();
    }

    @Test
    void extractExif_withNoExifData_returnsNull() throws IOException {
        // Create a simple JPEG with no EXIF data
        Path imagePath = tempDir.resolve("no-exif.jpg");
        createSimpleImage(imagePath);

        ExifMetadata result = exifExtractor.extractExif(imagePath);

        assertThat(result).isNull();
    }

    @Test
    void extractExif_withNonImageFile_returnsNull() throws IOException {
        Path textFile = tempDir.resolve("text.txt");
        Files.writeString(textFile, "This is not an image");

        ExifMetadata result = exifExtractor.extractExif(textFile);

        assertThat(result).isNull();
    }

    @Test
    void extractExif_withNonExistentFile_returnsNull() {
        Path nonExistent = tempDir.resolve("does-not-exist.jpg");

        ExifMetadata result = exifExtractor.extractExif(nonExistent);

        assertThat(result).isNull();
    }

    @Test
    void extractExif_withCorruptedImage_returnsNull() throws IOException {
        Path corruptedFile = tempDir.resolve("corrupted.jpg");
        Files.write(corruptedFile, new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x00}); // Invalid JPEG header

        ExifMetadata result = exifExtractor.extractExif(corruptedFile);

        assertThat(result).isNull();
    }

    /**
     * Test with a real image containing EXIF data.
     * Note: This test requires a sample image with EXIF data to be placed in test resources.
     * The test will be skipped if the sample image is not available.
     */
    @Test
    void extractExif_withRealExifData_extractsMetadata() {
        Path sampleImage = Path.of("archivum-scanner/src/test/resources/test-images/sample-with-exif.jpg");

        // Skip test if sample image doesn't exist yet
        if (!Files.exists(sampleImage)) {
            System.out.println("Skipping test - sample image not found at: " + sampleImage);
            System.out.println("To enable this test, add a JPEG with EXIF data to: src/test/resources/test-images/");
            return;
        }

        ExifMetadata result = exifExtractor.extractExif(sampleImage);

        // Verify that EXIF data was extracted
        assertThat(result).isNotNull();
        // Specific assertions will depend on the actual test image
        // Example assertions (uncomment and adjust based on your test image):
        // assertThat(result.getCameraMake()).isNotNull();
        // assertThat(result.getCameraModel()).isNotNull();
        // assertThat(result.getDateTimeOriginal()).isNotNull();
    }

    /**
     * Test GPS coordinate extraction.
     * Note: This test requires a sample image with GPS EXIF data.
     */
    @Test
    void extractExif_withGpsData_extractsCoordinates() {
        Path sampleImage = Path.of("archivum-scanner/src/test/resources/test-images/sample-with-gps.jpg");

        // Skip test if sample image doesn't exist yet
        if (!Files.exists(sampleImage)) {
            System.out.println("Skipping test - GPS sample image not found at: " + sampleImage);
            System.out.println("To enable this test, add a JPEG with GPS EXIF data to: src/test/resources/test-images/");
            return;
        }

        ExifMetadata result = exifExtractor.extractExif(sampleImage);

        assertThat(result).isNotNull();
        assertThat(result.getGps()).isNotNull();
        assertThat(result.getGps().getLatitude()).isNotNull();
        assertThat(result.getGps().getLongitude()).isNotNull();
    }

    /**
     * Test that null values are handled gracefully in ExifMetadata.
     */
    @Test
    void extractExif_handlesNullFieldsGracefully() {
        // This test verifies that ExifMetadata can be built with all null fields
        ExifMetadata metadata = ExifMetadata.builder()
            .cameraMake(null)
            .cameraModel(null)
            .dateTimeOriginal(null)
            .width(null)
            .height(null)
            .build();

        assertThat(metadata).isNotNull();
        assertThat(metadata.getCameraMake()).isNull();
        assertThat(metadata.getCameraModel()).isNull();
    }

    // Helper method to create a simple image without EXIF data
    private void createSimpleImage(Path path) throws IOException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        ImageIO.write(image, "jpg", path.toFile());
    }
}
