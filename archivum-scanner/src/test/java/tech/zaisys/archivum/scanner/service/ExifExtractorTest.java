package tech.zaisys.archivum.scanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.ExifMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExifExtractorTest {

    private ExifExtractor exifExtractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        exifExtractor = new ExifExtractor();
    }

    @Test
    void extractExif_nonImageFile_returnsNull() throws IOException {
        // Given - a text file
        Path textFile = tempDir.resolve("document.txt");
        Files.writeString(textFile, "This is not an image");

        // When
        ExifMetadata exif = exifExtractor.extractExif(textFile);

        // Then
        assertNull(exif, "Should return null for non-image files");
    }

    @Test
    void extractExif_emptyFile_returnsNull() throws IOException {
        // Given - an empty file
        Path emptyFile = tempDir.resolve("empty.jpg");
        Files.createFile(emptyFile);

        // When
        ExifMetadata exif = exifExtractor.extractExif(emptyFile);

        // Then
        assertNull(exif, "Should return null for empty/corrupted files");
    }

    @Test
    void extractExif_corruptedImage_returnsNull() throws IOException {
        // Given - a file with .jpg extension but corrupted content
        Path corruptedFile = tempDir.resolve("corrupted.jpg");
        Files.writeString(corruptedFile, "Not a valid JPEG file");

        // When
        ExifMetadata exif = exifExtractor.extractExif(corruptedFile);

        // Then
        assertNull(exif, "Should return null and not throw for corrupted images");
    }

    @Test
    void extractExif_imageWithoutExif_returnsNull() throws IOException {
        // Given - a minimal valid JPEG without EXIF data
        // JPEG header: FF D8 FF E0 00 10 4A 46 49 46 00 01 01 00 00 01 00 01 00 00
        // JPEG footer: FF D9
        Path jpegWithoutExif = tempDir.resolve("no-exif.jpg");
        byte[] minimalJpeg = new byte[]{
            (byte) 0xFF, (byte) 0xD8, // SOI marker
            (byte) 0xFF, (byte) 0xE0, // APP0 marker
            0x00, 0x10, // Length
            0x4A, 0x46, 0x49, 0x46, 0x00, // JFIF
            0x01, 0x01, // Version
            0x00, // Density units
            0x00, 0x01, // X density
            0x00, 0x01, // Y density
            0x00, 0x00, // Thumbnail
            (byte) 0xFF, (byte) 0xD9 // EOI marker
        };
        Files.write(jpegWithoutExif, minimalJpeg);

        // When
        ExifMetadata exif = exifExtractor.extractExif(jpegWithoutExif);

        // Then
        // Should return null since there's no EXIF data (only JFIF)
        assertNull(exif, "Should return null for images without EXIF data");
    }

    @Test
    void extractExif_nonExistentFile_returnsNull() {
        // Given - a path to non-existent file
        Path nonExistent = tempDir.resolve("does-not-exist.jpg");

        // When
        ExifMetadata exif = exifExtractor.extractExif(nonExistent);

        // Then
        assertNull(exif, "Should return null for non-existent files");
    }

    @Test
    void extractExif_pngWithoutExif_returnsNull() throws IOException {
        // Given - a minimal valid PNG without EXIF
        // PNG header + minimal IHDR chunk + IEND
        Path pngWithoutExif = tempDir.resolve("no-exif.png");
        byte[] minimalPng = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, // IHDR length
            0x49, 0x48, 0x44, 0x52, // IHDR
            0x00, 0x00, 0x00, 0x01, // Width = 1
            0x00, 0x00, 0x00, 0x01, // Height = 1
            0x08, 0x02, 0x00, 0x00, 0x00, // Bit depth, color type, compression, filter, interlace
            (byte) 0x90, 0x77, 0x53, (byte) 0xDE, // CRC
            0x00, 0x00, 0x00, 0x00, // IEND length
            0x49, 0x45, 0x4E, 0x44, // IEND
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82 // CRC
        };
        Files.write(pngWithoutExif, minimalPng);

        // When
        ExifMetadata exif = exifExtractor.extractExif(pngWithoutExif);

        // Then
        assertNull(exif, "Should return null for PNG without EXIF data");
    }

    // Note: The following integration tests are deferred to future improvements (low priority)
    // They would require actual JPEG files with EXIF data in src/test/resources/test-images/
    //
    // Future Improvement - Low Priority:
    // Add integration tests with real EXIF images to verify:
    // - Camera make/model extraction
    // - Date/time original extraction
    // - Dimensions, orientation extraction
    // - GPS coordinates (latitude, longitude, altitude)
    // - Lens model, focal length, aperture, shutter speed, ISO, flash
    // - Handling of partial EXIF data (missing fields)

    @Test
    void extractExif_validJpegWithExif_extractsMetadata() {
        // TODO: Future improvement - Add integration test with real EXIF JPEG
        // Expected behavior:
        // - Should extract camera make/model if present
        // - Should extract date/time original if present
        // - Should extract dimensions if present
        // - Should handle partial EXIF data gracefully
    }

    @Test
    void extractExif_jpegWithGps_extractsGpsCoordinates() {
        // TODO: Future improvement - Add integration test with GPS EXIF JPEG
        // Expected behavior:
        // - Should extract latitude, longitude, altitude
        // - GPS coordinates should be within valid ranges
        // - Should handle missing altitude gracefully
    }
}
