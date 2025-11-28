package tech.zaisys.archivum.scanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.enums.FileStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MetadataServiceTest {

    private MetadataService metadataService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        metadataService = new MetadataService();
    }

    @Test
    void extractMetadata_validFile_extractsBasicMetadata() throws IOException {
        // Given
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Test content");
        UUID sourceId = UUID.randomUUID();
        String hash = "abc123";

        // When
        FileDto dto = metadataService.extractMetadata(file, sourceId, hash, false);

        // Then
        assertNotNull(dto);
        assertEquals("test.txt", dto.getName());
        assertEquals("txt", dto.getExtension());
        assertEquals(12, dto.getSize()); // "Test content" = 12 bytes
        assertEquals(hash, dto.getSha256());
        assertEquals(sourceId, dto.getSourceId());
        assertEquals(FileStatus.HASHED, dto.getStatus());
        assertNotNull(dto.getId());
        assertNotNull(dto.getModifiedAt());
        assertNotNull(dto.getCreatedAt());
        assertNotNull(dto.getScannedAt());
    }

    @Test
    void extractMetadata_fileWithoutExtension_hasEmptyExtension() throws IOException {
        // Given
        Path file = tempDir.resolve("noextension");
        Files.createFile(file);
        UUID sourceId = UUID.randomUUID();

        // When
        FileDto dto = metadataService.extractMetadata(file, sourceId, "hash", false);

        // Then
        assertEquals("", dto.getExtension());
        assertEquals("noextension", dto.getName());
    }

    @Test
    void extractMetadata_hiddenFile_extractsCorrectly() throws IOException {
        // Given
        Path file = tempDir.resolve(".hidden");
        Files.createFile(file);
        UUID sourceId = UUID.randomUUID();

        // When
        FileDto dto = metadataService.extractMetadata(file, sourceId, "hash", false);

        // Then
        assertEquals(".hidden", dto.getName());
        assertEquals("", dto.getExtension());
    }

    @Test
    void extractMetadata_fileWithMultipleDots_extractsLastExtension() throws IOException {
        // Given
        Path file = tempDir.resolve("archive.tar.gz");
        Files.createFile(file);
        UUID sourceId = UUID.randomUUID();

        // When
        FileDto dto = metadataService.extractMetadata(file, sourceId, "hash", false);

        // Then
        assertEquals("archive.tar.gz", dto.getName());
        assertEquals("tar.gz", dto.getExtension());
    }

    @Test
    void extractMetadata_uppercaseExtension_convertsToLowercase() throws IOException {
        // Given
        Path file = tempDir.resolve("document.PDF");
        Files.createFile(file);
        UUID sourceId = UUID.randomUUID();

        // When
        FileDto dto = metadataService.extractMetadata(file, sourceId, "hash", false);

        // Then
        assertEquals("pdf", dto.getExtension());
    }

    @Test
    void isImageFile_validImageExtensions_returnsTrue() throws IOException {
        // Given
        Path jpg = tempDir.resolve("photo.jpg");
        Path jpeg = tempDir.resolve("photo.jpeg");
        Path png = tempDir.resolve("image.png");
        Path heic = tempDir.resolve("photo.heic");

        Files.createFile(jpg);
        Files.createFile(jpeg);
        Files.createFile(png);
        Files.createFile(heic);

        // When & Then
        assertTrue(metadataService.isImageFile(jpg));
        assertTrue(metadataService.isImageFile(jpeg));
        assertTrue(metadataService.isImageFile(png));
        assertTrue(metadataService.isImageFile(heic));
    }

    @Test
    void isImageFile_nonImageExtension_returnsFalse() throws IOException {
        // Given
        Path txt = tempDir.resolve("document.txt");
        Files.createFile(txt);

        // When & Then
        assertFalse(metadataService.isImageFile(txt));
    }

    @Test
    void isVideoFile_validVideoExtensions_returnsTrue() throws IOException {
        // Given
        Path mp4 = tempDir.resolve("video.mp4");
        Path mov = tempDir.resolve("video.mov");
        Path mkv = tempDir.resolve("video.mkv");

        Files.createFile(mp4);
        Files.createFile(mov);
        Files.createFile(mkv);

        // When & Then
        assertTrue(metadataService.isVideoFile(mp4));
        assertTrue(metadataService.isVideoFile(mov));
        assertTrue(metadataService.isVideoFile(mkv));
    }

    @Test
    void isVideoFile_nonVideoExtension_returnsFalse() throws IOException {
        // Given
        Path txt = tempDir.resolve("document.txt");
        Files.createFile(txt);

        // When & Then
        assertFalse(metadataService.isVideoFile(txt));
    }

    @Test
    void isArchiveFile_validArchiveExtensions_returnsTrue() throws IOException {
        // Given
        Path zip = tempDir.resolve("archive.zip");
        Path tar = tempDir.resolve("archive.tar");
        Path gz = tempDir.resolve("archive.tar.gz");
        Path sevenZ = tempDir.resolve("archive.7z");

        Files.createFile(zip);
        Files.createFile(tar);
        Files.createFile(gz);
        Files.createFile(sevenZ);

        // When & Then
        assertTrue(metadataService.isArchiveFile(zip));
        assertTrue(metadataService.isArchiveFile(tar));
        assertTrue(metadataService.isArchiveFile(gz));
        assertTrue(metadataService.isArchiveFile(sevenZ));
    }

    @Test
    void isArchiveFile_nonArchiveExtension_returnsFalse() throws IOException {
        // Given
        Path txt = tempDir.resolve("document.txt");
        Files.createFile(txt);

        // When & Then
        assertFalse(metadataService.isArchiveFile(txt));
    }

    @Test
    void extractMetadata_preservesTimestamps() throws IOException {
        // Given
        Path file = tempDir.resolve("timestamped.txt");
        Files.createFile(file);

        Instant expectedModified = Instant.now().minus(1, ChronoUnit.DAYS);
        Files.setLastModifiedTime(file, FileTime.from(expectedModified));

        UUID sourceId = UUID.randomUUID();

        // When
        FileDto dto = metadataService.extractMetadata(file, sourceId, "hash", false);

        // Then
        assertNotNull(dto.getModifiedAt());
        // Allow 1 second tolerance due to filesystem precision
        assertTrue(Math.abs(dto.getModifiedAt().toEpochMilli() - expectedModified.toEpochMilli()) < 1000);
    }

    @Test
    void extractMetadata_nonExistentFile_throwsIOException() {
        // Given
        Path nonExistent = tempDir.resolve("does-not-exist.txt");
        UUID sourceId = UUID.randomUUID();

        // When & Then
        assertThrows(IOException.class, () ->
            metadataService.extractMetadata(nonExistent, sourceId, "hash", false)
        );
    }
}
