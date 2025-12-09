package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.domain.FolderZone;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.FolderZoneRepository;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FolderZoneService")
class FolderZoneServiceTest {

    @Mock
    private FolderZoneRepository folderZoneRepository;

    @Mock
    private SourceRepository sourceRepository;

    @InjectMocks
    private FolderZoneService folderZoneService;

    private UUID sourceId;
    private Source source;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        source = new Source();
        source.setId(sourceId);
    }

    @Test
    @DisplayName("should load folder zones as map")
    void shouldLoadFolderZonesAsMap() {
        // Given
        FolderZone zone1 = createFolderZone("/path/to/folder1", Zone.MEDIA);
        FolderZone zone2 = createFolderZone("/path/to/folder2", Zone.DOCUMENTS);
        when(folderZoneRepository.findBySourceId(sourceId))
            .thenReturn(List.of(zone1, zone2));

        // When
        Map<String, Zone> result = folderZoneService.loadFolderZones(sourceId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get("/path/to/folder1")).isEqualTo(Zone.MEDIA);
        assertThat(result.get("/path/to/folder2")).isEqualTo(Zone.DOCUMENTS);
    }

    @Test
    @DisplayName("should return empty map when no zones exist")
    void shouldReturnEmptyMapWhenNoZonesExist() {
        // Given
        when(folderZoneRepository.findBySourceId(sourceId))
            .thenReturn(List.of());

        // When
        Map<String, Zone> result = folderZoneService.loadFolderZones(sourceId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should get explicit zone for folder")
    void shouldGetExplicitZoneForFolder() {
        // Given
        Map<String, Zone> zoneMap = Map.of("/path/to/folder", Zone.MEDIA);

        // When
        FolderZoneService.ZoneResult result = folderZoneService.getZoneForFolder(
            sourceId, "/path/to/folder", zoneMap);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.zone()).isEqualTo(Zone.MEDIA);
        assertThat(result.isInherited()).isFalse();
    }

    @Test
    @DisplayName("should inherit zone from parent folder")
    void shouldInheritZoneFromParentFolder() {
        // Given
        Map<String, Zone> zoneMap = Map.of("/path/to", Zone.DOCUMENTS);

        // When
        FolderZoneService.ZoneResult result = folderZoneService.getZoneForFolder(
            sourceId, "/path/to/folder/subfolder", zoneMap);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.zone()).isEqualTo(Zone.DOCUMENTS);
        assertThat(result.isInherited()).isTrue();
    }

    @Test
    @DisplayName("should inherit zone from nearest parent")
    void shouldInheritZoneFromNearestParent() {
        // Given
        Map<String, Zone> zoneMap = Map.of(
            "/path", Zone.MEDIA,
            "/path/to", Zone.DOCUMENTS
        );

        // When - child of /path/to should inherit DOCUMENTS, not MEDIA
        FolderZoneService.ZoneResult result = folderZoneService.getZoneForFolder(
            sourceId, "/path/to/folder", zoneMap);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.zone()).isEqualTo(Zone.DOCUMENTS);
        assertThat(result.isInherited()).isTrue();
    }

    @Test
    @DisplayName("should return null when no zone found in hierarchy")
    void shouldReturnNullWhenNoZoneFoundInHierarchy() {
        // Given
        Map<String, Zone> zoneMap = Map.of("/other/path", Zone.MEDIA);

        // When
        FolderZoneService.ZoneResult result = folderZoneService.getZoneForFolder(
            sourceId, "/path/to/folder", zoneMap);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should set folder zone when folder zone does not exist")
    void shouldSetFolderZoneWhenNotExists() {
        // Given
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(folderZoneRepository.findBySourceIdAndFolderPath(sourceId, "/path/to/folder"))
            .thenReturn(Optional.empty());

        // When
        folderZoneService.setFolderZone(sourceId, "/path/to/folder", Zone.MEDIA);

        // Then
        verify(folderZoneRepository).save(argThat(folderZone ->
            folderZone.getSource().equals(source) &&
            folderZone.getFolderPath().equals("/path/to/folder") &&
            folderZone.getZone() == Zone.MEDIA
        ));
    }

    @Test
    @DisplayName("should update existing folder zone")
    void shouldUpdateExistingFolderZone() {
        // Given
        FolderZone existingZone = createFolderZone("/path/to/folder", Zone.DOCUMENTS);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(folderZoneRepository.findBySourceIdAndFolderPath(sourceId, "/path/to/folder"))
            .thenReturn(Optional.of(existingZone));

        // When
        folderZoneService.setFolderZone(sourceId, "/path/to/folder", Zone.MEDIA);

        // Then
        assertThat(existingZone.getZone()).isEqualTo(Zone.MEDIA);
        verify(folderZoneRepository).save(existingZone);
    }

    @Test
    @DisplayName("should throw exception when source not found")
    void shouldThrowExceptionWhenSourceNotFound() {
        // Given
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
            folderZoneService.setFolderZone(sourceId, "/path/to/folder", Zone.MEDIA))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Source not found");
    }


    @Test
    @DisplayName("should handle inheritance for root level folders")
    void shouldHandleInheritanceForRootLevelFolders() {
        // Given
        Map<String, Zone> zoneMap = Map.of();

        // When
        FolderZoneService.ZoneResult result = folderZoneService.getZoneForFolder(
            sourceId, "/folder", zoneMap);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should prefer explicit zone over inherited")
    void shouldPreferExplicitZoneOverInherited() {
        // Given
        Map<String, Zone> zoneMap = Map.of(
            "/path", Zone.MEDIA,
            "/path/to/folder", Zone.DOCUMENTS
        );

        // When
        FolderZoneService.ZoneResult result = folderZoneService.getZoneForFolder(
            sourceId, "/path/to/folder", zoneMap);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.zone()).isEqualTo(Zone.DOCUMENTS);
        assertThat(result.isInherited()).isFalse();
    }

    private FolderZone createFolderZone(String path, Zone zone) {
        FolderZone folderZone = new FolderZone();
        folderZone.setId(UUID.randomUUID());
        folderZone.setSource(source);
        folderZone.setFolderPath(path);
        folderZone.setZone(zone);
        return folderZone;
    }
}
