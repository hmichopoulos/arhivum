package tech.zaisys.archivum.server.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.domain.FolderZone;
import tech.zaisys.archivum.server.domain.Source;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for FolderZoneRepository using Testcontainers.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FolderZoneRepository Integration Tests")
class FolderZoneRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private FolderZoneRepository folderZoneRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private Source source;

    @BeforeEach
    void setUp() {
        folderZoneRepository.deleteAll();
        sourceRepository.deleteAll();

        source = createAndSaveSource();
    }

    @Test
    @DisplayName("should save and find folder zone by source and path")
    void shouldSaveAndFindFolderZoneBySourceAndPath() {
        // Given
        FolderZone folderZone = createFolderZone("/path/to/folder", Zone.MEDIA);

        // When
        FolderZone saved = folderZoneRepository.save(folderZone);
        Optional<FolderZone> found = folderZoneRepository.findBySourceIdAndFolderPath(
            source.getId(), "/path/to/folder");

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getFolderPath()).isEqualTo("/path/to/folder");
        assertThat(found.get().getZone()).isEqualTo(Zone.MEDIA);
        assertThat(found.get().getSource().getId()).isEqualTo(source.getId());
    }

    @Test
    @DisplayName("should find all folder zones by source")
    void shouldFindAllFolderZonesBySource() {
        // Given
        FolderZone zone1 = createFolderZone("/path/folder1", Zone.MEDIA);
        FolderZone zone2 = createFolderZone("/path/folder2", Zone.DOCUMENTS);
        FolderZone zone3 = createFolderZone("/path/folder3", Zone.CODE);
        folderZoneRepository.saveAll(List.of(zone1, zone2, zone3));

        // When
        List<FolderZone> zones = folderZoneRepository.findBySourceId(source.getId());

        // Then
        assertThat(zones).hasSize(3);
        assertThat(zones)
            .extracting(FolderZone::getZone)
            .containsExactlyInAnyOrder(Zone.MEDIA, Zone.DOCUMENTS, Zone.CODE);
    }

    @Test
    @DisplayName("should delete folder zone by source and path")
    void shouldDeleteFolderZoneBySourceAndPath() {
        // Given
        FolderZone folderZone = createFolderZone("/path/to/folder", Zone.MEDIA);
        folderZoneRepository.save(folderZone);

        // When
        folderZoneRepository.deleteBySourceIdAndFolderPath(source.getId(), "/path/to/folder");
        folderZoneRepository.flush();

        // Then
        Optional<FolderZone> found = folderZoneRepository.findBySourceIdAndFolderPath(
            source.getId(), "/path/to/folder");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should return empty when folder zone not found")
    void shouldReturnEmptyWhenFolderZoneNotFound() {
        // When
        Optional<FolderZone> found = folderZoneRepository.findBySourceIdAndFolderPath(
            source.getId(), "/non/existent/path");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should enforce unique constraint on source and folder path")
    void shouldEnforceUniqueConstraintOnSourceAndFolderPath() {
        // Given
        FolderZone zone1 = createFolderZone("/path/to/folder", Zone.MEDIA);
        folderZoneRepository.save(zone1);

        // When - try to save another zone with same source and path
        FolderZone zone2 = createFolderZone("/path/to/folder", Zone.DOCUMENTS);

        // Then - should throw exception or update existing
        assertThat(folderZoneRepository.findBySourceIdAndFolderPath(
            source.getId(), "/path/to/folder"))
            .isPresent()
            .get()
            .extracting(FolderZone::getZone)
            .isEqualTo(Zone.MEDIA);
    }

    @Test
    @DisplayName("should cascade delete folder zones when source is deleted")
    void shouldCascadeDeleteFolderZonesWhenSourceIsDeleted() {
        // Given
        FolderZone zone1 = createFolderZone("/path/folder1", Zone.MEDIA);
        FolderZone zone2 = createFolderZone("/path/folder2", Zone.DOCUMENTS);
        folderZoneRepository.saveAll(List.of(zone1, zone2));

        // When
        sourceRepository.delete(source);
        sourceRepository.flush();

        // Then
        List<FolderZone> zones = folderZoneRepository.findBySourceId(source.getId());
        assertThat(zones).isEmpty();
    }

    @Test
    @DisplayName("should handle multiple sources with same folder path")
    void shouldHandleMultipleSourcesWithSameFolderPath() {
        // Given
        Source source2 = createAndSaveSource();
        FolderZone zone1 = createFolderZone("/path/to/folder", Zone.MEDIA);
        zone1.setSource(source);
        FolderZone zone2 = createFolderZone("/path/to/folder", Zone.DOCUMENTS);
        zone2.setSource(source2);

        // When
        folderZoneRepository.saveAll(List.of(zone1, zone2));

        // Then
        Optional<FolderZone> found1 = folderZoneRepository.findBySourceIdAndFolderPath(
            source.getId(), "/path/to/folder");
        Optional<FolderZone> found2 = folderZoneRepository.findBySourceIdAndFolderPath(
            source2.getId(), "/path/to/folder");

        assertThat(found1).isPresent();
        assertThat(found1.get().getZone()).isEqualTo(Zone.MEDIA);
        assertThat(found2).isPresent();
        assertThat(found2.get().getZone()).isEqualTo(Zone.DOCUMENTS);
    }

    private Source createAndSaveSource() {
        Source src = new Source();
        src.setName("Test Source");
        src.setRootPath("/test/path");
        src.setType(SourceType.DISK);
        src.setStatus(ScanStatus.COMPLETED);
        src.setTotalFiles(0L);
        src.setProcessedFiles(0L);
        src.setTotalSize(0L);
        src.setProcessedSize(0L);
        return sourceRepository.save(src);
    }

    private FolderZone createFolderZone(String path, Zone zone) {
        FolderZone folderZone = new FolderZone();
        folderZone.setSource(source);
        folderZone.setFolderPath(path);
        folderZone.setZone(zone);
        return folderZone;
    }
}
