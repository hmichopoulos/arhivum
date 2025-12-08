package tech.zaisys.archivum.server.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.zaisys.archivum.api.dto.ExifMetadata;
import tech.zaisys.archivum.api.dto.GpsCoordinates;
import tech.zaisys.archivum.api.enums.FileStatus;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.domain.Source;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ScannedFileRepository using Testcontainers.
 * These tests require Docker to be running.
 *
 * To run these tests locally, set environment variable: DOCKER_HOST or ensure Docker is running.
 * These tests will be skipped in CI/environments without Docker unless TESTCONTAINERS_ENABLED=true.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScannedFileRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("archivum_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ScannedFileRepository scannedFileRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private Source testSource;
    private ScannedFile testFile;

    @BeforeEach
    void setUp() {
        scannedFileRepository.deleteAll();
        sourceRepository.deleteAll();

        // Create test source
        testSource = Source.builder()
            .name("Test Disk")
            .type(SourceType.DISK)
            .rootPath("/mnt/test")
            .status(ScanStatus.PENDING)
            .postponed(false)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .build();
        testSource = sourceRepository.save(testSource);

        // Create test file
        testFile = ScannedFile.builder()
            .source(testSource)
            .path("photos/vacation.jpg")
            .name("vacation.jpg")
            .extension("jpg")
            .size(1024000L)
            .sha256("a1b2c3d4e5f6" + "0".repeat(52)) // 64 char hash
            .modifiedAt(Instant.now())
            .createdAt(Instant.now())
            .mimeType("image/jpeg")
            .status(FileStatus.HASHED)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();
    }

    @Test
    void testSaveAndFind() {
        // Given/When
        ScannedFile saved = scannedFileRepository.save(testFile);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<ScannedFile> found = scannedFileRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("photos/vacation.jpg", found.get().getPath());
        assertEquals("vacation.jpg", found.get().getName());
        assertEquals("jpg", found.get().getExtension());
        assertEquals(1024000L, found.get().getSize());
        assertEquals(FileStatus.HASHED, found.get().getStatus());
        assertFalse(found.get().getIsDuplicate());
    }

    @Test
    void testExifJsonbStorage() {
        // Given
        GpsCoordinates gps = GpsCoordinates.builder()
            .latitude(48.8566)
            .longitude(2.3522)
            .build();

        ExifMetadata exif = ExifMetadata.builder()
            .cameraMake("Canon")
            .cameraModel("EOS 5D Mark IV")
            .dateTimeOriginal(Instant.parse("2023-06-15T14:30:00Z"))
            .width(6720)
            .height(4480)
            .orientation(1)
            .gps(gps)
            .lensModel("EF24-70mm f/2.8L II USM")
            .focalLength(50.0)
            .aperture(2.8)
            .shutterSpeed("1/250")
            .iso(400)
            .flash(false)
            .build();

        testFile.setExifMetadata(exif);

        // When
        ScannedFile saved = scannedFileRepository.save(testFile);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<ScannedFile> found = scannedFileRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertNotNull(found.get().getExifMetadata());
        assertEquals("Canon", found.get().getExifMetadata().getCameraMake());
        assertEquals("EOS 5D Mark IV", found.get().getExifMetadata().getCameraModel());
        assertEquals(6720, found.get().getExifMetadata().getWidth());
        assertEquals(4480, found.get().getExifMetadata().getHeight());
        assertNotNull(found.get().getExifMetadata().getGps());
        assertEquals(48.8566, found.get().getExifMetadata().getGps().getLatitude());
        assertEquals(2.3522, found.get().getExifMetadata().getGps().getLongitude());
    }

    @Test
    void testUniqueConstraintSourcePath() {
        // Given
        scannedFileRepository.save(testFile);
        entityManager.flush();

        // When/Then - Attempting to save duplicate path for same source
        ScannedFile duplicate = ScannedFile.builder()
            .source(testSource)
            .path("photos/vacation.jpg") // Same path
            .name("vacation.jpg")
            .extension("jpg")
            .size(2048000L)
            .sha256("different_hash" + "0".repeat(50))
            .scannedAt(Instant.now())
            .build();

        assertThrows(Exception.class, () -> {
            scannedFileRepository.save(duplicate);
            entityManager.flush();
        });
    }

    @Test
    void testFindBySourceId() {
        // Given
        scannedFileRepository.save(testFile);

        ScannedFile file2 = ScannedFile.builder()
            .source(testSource)
            .path("photos/beach.jpg")
            .name("beach.jpg")
            .extension("jpg")
            .size(2048000L)
            .sha256("different_hash" + "0".repeat(50))
            .scannedAt(Instant.now())
            .build();
        scannedFileRepository.save(file2);
        entityManager.flush();

        // When
        List<ScannedFile> files = scannedFileRepository.findBySourceId(testSource.getId());

        // Then
        assertEquals(2, files.size());
    }

    @Test
    void testFindBySourceIdAndPath() {
        // Given
        scannedFileRepository.save(testFile);
        entityManager.flush();

        // When
        Optional<ScannedFile> found = scannedFileRepository.findBySourceIdAndPath(
            testSource.getId(), "photos/vacation.jpg");

        // Then
        assertTrue(found.isPresent());
        assertEquals("vacation.jpg", found.get().getName());
    }

    @Test
    void testFindBySha256() {
        // Given
        String hash = "a1b2c3d4e5f6" + "0".repeat(52);
        scannedFileRepository.save(testFile);

        ScannedFile duplicate = ScannedFile.builder()
            .source(testSource)
            .path("photos/vacation_copy.jpg")
            .name("vacation_copy.jpg")
            .extension("jpg")
            .size(1024000L)
            .sha256(hash) // Same hash
            .scannedAt(Instant.now())
            .build();
        scannedFileRepository.save(duplicate);
        entityManager.flush();

        // When
        List<ScannedFile> files = scannedFileRepository.findBySha256(hash);

        // Then
        assertEquals(2, files.size());
    }

    @Test
    void testExistsBySha256() {
        // Given
        String hash = "a1b2c3d4e5f6" + "0".repeat(52);
        scannedFileRepository.save(testFile);
        entityManager.flush();

        // When/Then
        assertTrue(scannedFileRepository.existsBySha256(hash));
        assertFalse(scannedFileRepository.existsBySha256("nonexistent_hash" + "0".repeat(50)));
    }

    @Test
    void testCountBySha256() {
        // Given
        String hash = "a1b2c3d4e5f6" + "0".repeat(52);
        scannedFileRepository.save(testFile);
        entityManager.flush();

        // When
        long count = scannedFileRepository.countBySha256(hash);

        // Then
        assertEquals(1, count);
    }

    @Test
    void testFindByStatus() {
        // Given
        scannedFileRepository.save(testFile);

        ScannedFile duplicateFile = ScannedFile.builder()
            .source(testSource)
            .path("photos/dup.jpg")
            .name("dup.jpg")
            .extension("jpg")
            .size(512000L)
            .sha256("other_hash" + "0".repeat(54))
            .status(FileStatus.DUPLICATE)
            .isDuplicate(true)
            .scannedAt(Instant.now())
            .build();
        scannedFileRepository.save(duplicateFile);
        entityManager.flush();

        // When
        List<ScannedFile> hashedFiles = scannedFileRepository.findByStatus(FileStatus.HASHED);
        List<ScannedFile> duplicateFiles = scannedFileRepository.findByStatus(FileStatus.DUPLICATE);

        // Then
        assertEquals(1, hashedFiles.size());
        assertEquals(1, duplicateFiles.size());
    }

    @Test
    void testFindByIsDuplicateTrue() {
        // Given
        scannedFileRepository.save(testFile);

        ScannedFile duplicateFile = ScannedFile.builder()
            .source(testSource)
            .path("photos/dup.jpg")
            .name("dup.jpg")
            .extension("jpg")
            .size(512000L)
            .sha256("other_hash" + "0".repeat(54))
            .isDuplicate(true)
            .scannedAt(Instant.now())
            .build();
        scannedFileRepository.save(duplicateFile);
        entityManager.flush();

        // When
        List<ScannedFile> duplicates = scannedFileRepository.findByIsDuplicateTrue();

        // Then
        assertEquals(1, duplicates.size());
        assertTrue(duplicates.get(0).getIsDuplicate());
    }

    @Test
    void testFindByExtension() {
        // Given
        scannedFileRepository.save(testFile);

        ScannedFile pdfFile = ScannedFile.builder()
            .source(testSource)
            .path("documents/report.pdf")
            .name("report.pdf")
            .extension("pdf")
            .size(256000L)
            .sha256("pdf_hash" + "0".repeat(56))
            .scannedAt(Instant.now())
            .build();
        scannedFileRepository.save(pdfFile);
        entityManager.flush();

        // When
        List<ScannedFile> jpgFiles = scannedFileRepository.findByExtension("jpg");
        List<ScannedFile> pdfFiles = scannedFileRepository.findByExtension("pdf");

        // Then
        assertEquals(1, jpgFiles.size());
        assertEquals(1, pdfFiles.size());
    }

    @Test
    void testCascadeDeleteWithSource() {
        // Given
        ScannedFile saved = scannedFileRepository.save(testFile);
        entityManager.flush();

        // When
        sourceRepository.delete(testSource);
        entityManager.flush();

        // Then
        Optional<ScannedFile> found = scannedFileRepository.findById(saved.getId());
        assertFalse(found.isPresent()); // File should be deleted when source is deleted
    }

    @Test
    void testSelfReferencingOriginalFile() {
        // Given
        ScannedFile original = scannedFileRepository.save(testFile);
        entityManager.flush();

        ScannedFile duplicate = ScannedFile.builder()
            .source(testSource)
            .path("photos/vacation_copy.jpg")
            .name("vacation_copy.jpg")
            .extension("jpg")
            .size(1024000L)
            .sha256(testFile.getSha256())
            .isDuplicate(true)
            .originalFile(original) // Reference to original
            .scannedAt(Instant.now())
            .build();
        scannedFileRepository.save(duplicate);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<ScannedFile> foundDuplicate = scannedFileRepository.findById(duplicate.getId());

        // Then
        assertTrue(foundDuplicate.isPresent());
        assertTrue(foundDuplicate.get().getIsDuplicate());
        assertNotNull(foundDuplicate.get().getOriginalFile());
        assertEquals(original.getId(), foundDuplicate.get().getOriginalFile().getId());
    }
}
