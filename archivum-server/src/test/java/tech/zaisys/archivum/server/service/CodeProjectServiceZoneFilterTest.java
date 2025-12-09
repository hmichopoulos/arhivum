package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.domain.CodeProject;
import tech.zaisys.archivum.server.repository.CodeProjectRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeProjectService Zone Filtering Tests")
class CodeProjectServiceZoneFilterTest {

    @Mock
    private CodeProjectRepository repository;

    @Mock
    private FolderZoneService folderZoneService;

    @InjectMocks
    private CodeProjectService codeProjectService;

    private UUID sourceId;
    private CodeProject codeProject;
    private CodeProject mediaProject;
    private CodeProject noZoneProject;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();

        // Project in CODE zone
        codeProject = createCodeProject("/projects/my-app", "my-app");

        // Project that will be in MEDIA zone (reclassified)
        mediaProject = createCodeProject("/projects/photos-app", "photos-app");

        // Project with no zone set
        noZoneProject = createCodeProject("/projects/another-app", "another-app");
    }

    @Test
    @DisplayName("should include project with explicit CODE zone")
    void shouldIncludeProjectWithExplicitCodeZone() {
        // Given
        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(List.of(codeProject));
        when(folderZoneService.loadFolderZones(sourceId))
            .thenReturn(Map.of("/projects/my-app", Zone.CODE));
        when(folderZoneService.getZoneForFolder(sourceId, "/projects/my-app",
            Map.of("/projects/my-app", Zone.CODE)))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.CODE, false));

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRootPath()).isEqualTo("/projects/my-app");
    }

    @Test
    @DisplayName("should exclude project with explicit MEDIA zone")
    void shouldExcludeProjectWithExplicitMediaZone() {
        // Given
        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(List.of(mediaProject));
        when(folderZoneService.loadFolderZones(sourceId))
            .thenReturn(Map.of("/projects/photos-app", Zone.MEDIA));
        when(folderZoneService.getZoneForFolder(sourceId, "/projects/photos-app",
            Map.of("/projects/photos-app", Zone.MEDIA)))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.MEDIA, false));

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should include project with no zone set (default behavior)")
    void shouldIncludeProjectWithNoZoneSet() {
        // Given
        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(List.of(noZoneProject));
        when(folderZoneService.loadFolderZones(sourceId)).thenReturn(Map.of());
        when(folderZoneService.getZoneForFolder(sourceId, "/projects/another-app", Map.of()))
            .thenReturn(null);

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRootPath()).isEqualTo("/projects/another-app");
    }

    @Test
    @DisplayName("should include project with inherited CODE zone")
    void shouldIncludeProjectWithInheritedCodeZone() {
        // Given - parent folder has CODE zone
        CodeProject childProject = createCodeProject("/projects/parent/child-app", "child-app");
        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(List.of(childProject));
        when(folderZoneService.loadFolderZones(sourceId))
            .thenReturn(Map.of("/projects/parent", Zone.CODE));
        when(folderZoneService.getZoneForFolder(sourceId, "/projects/parent/child-app",
            Map.of("/projects/parent", Zone.CODE)))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.CODE, true));

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRootPath()).isEqualTo("/projects/parent/child-app");
    }

    @Test
    @DisplayName("should exclude project with inherited DOCUMENTS zone")
    void shouldExcludeProjectWithInheritedDocumentsZone() {
        // Given - parent folder has DOCUMENTS zone
        CodeProject childProject = createCodeProject("/documents/old-projects/legacy-app", "legacy-app");
        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(List.of(childProject));
        when(folderZoneService.loadFolderZones(sourceId))
            .thenReturn(Map.of("/documents", Zone.DOCUMENTS));
        when(folderZoneService.getZoneForFolder(sourceId, "/documents/old-projects/legacy-app",
            Map.of("/documents", Zone.DOCUMENTS)))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.DOCUMENTS, true));

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should filter mixed projects correctly")
    void shouldFilterMixedProjectsCorrectly() {
        // Given - mix of CODE, MEDIA, and no zone projects
        when(repository.findAllByOrderByScannedAtDesc())
            .thenReturn(List.of(codeProject, mediaProject, noZoneProject));

        Map<String, Zone> zoneMap = Map.of(
            "/projects/my-app", Zone.CODE,
            "/projects/photos-app", Zone.MEDIA
        );

        when(folderZoneService.loadFolderZones(sourceId)).thenReturn(zoneMap);

        when(folderZoneService.getZoneForFolder(sourceId, "/projects/my-app", zoneMap))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.CODE, false));
        when(folderZoneService.getZoneForFolder(sourceId, "/projects/photos-app", zoneMap))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.MEDIA, false));
        when(folderZoneService.getZoneForFolder(sourceId, "/projects/another-app", zoneMap))
            .thenReturn(null);

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then - should include CODE project and no-zone project, exclude MEDIA project
        assertThat(result).hasSize(2);
        assertThat(result)
            .extracting(CodeProjectDto::getRootPath)
            .containsExactlyInAnyOrder("/projects/my-app", "/projects/another-app");
    }

    @Test
    @DisplayName("should handle multiple sources correctly")
    void shouldHandleMultipleSourcesCorrectly() {
        // Given - projects from two different sources
        UUID source1 = UUID.randomUUID();
        UUID source2 = UUID.randomUUID();

        CodeProject project1 = createCodeProjectWithSource(source1, "/source1/app1", "app1");
        CodeProject project2 = createCodeProjectWithSource(source2, "/source2/app2", "app2");

        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(List.of(project1, project2));

        // Source 1: app1 is in CODE zone
        Map<String, Zone> zone1 = Map.of("/source1/app1", Zone.CODE);
        when(folderZoneService.loadFolderZones(source1)).thenReturn(zone1);
        when(folderZoneService.getZoneForFolder(source1, "/source1/app1", zone1))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.CODE, false));

        // Source 2: app2 is in MEDIA zone
        Map<String, Zone> zone2 = Map.of("/source2/app2", Zone.MEDIA);
        when(folderZoneService.loadFolderZones(source2)).thenReturn(zone2);
        when(folderZoneService.getZoneForFolder(source2, "/source2/app2", zone2))
            .thenReturn(new FolderZoneService.ZoneResult(Zone.MEDIA, false));

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then - should only include project from source1
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRootPath()).isEqualTo("/source1/app1");
        assertThat(result.get(0).getSourceId()).isEqualTo(source1);

        // Verify zone loading was called for both sources
        verify(folderZoneService).loadFolderZones(source1);
        verify(folderZoneService).loadFolderZones(source2);
    }

    @Test
    @DisplayName("should exclude all non-CODE zones")
    void shouldExcludeAllNonCodeZones() {
        // Given - projects in various non-CODE zones
        List<CodeProject> projects = List.of(
            createCodeProject("/media/photos", "photos"),
            createCodeProject("/documents/reports", "reports"),
            createCodeProject("/books/ebooks", "ebooks"),
            createCodeProject("/software/installers", "installers"),
            createCodeProject("/backup/archives", "archives")
        );

        when(repository.findAllByOrderByScannedAtDesc()).thenReturn(projects);

        Map<String, Zone> zoneMap = Map.of(
            "/media/photos", Zone.MEDIA,
            "/documents/reports", Zone.DOCUMENTS,
            "/books/ebooks", Zone.BOOKS,
            "/software/installers", Zone.SOFTWARE,
            "/backup/archives", Zone.BACKUP
        );

        when(folderZoneService.loadFolderZones(sourceId)).thenReturn(zoneMap);

        for (CodeProject project : projects) {
            Zone zone = zoneMap.get(project.getRootPath());
            when(folderZoneService.getZoneForFolder(sourceId, project.getRootPath(), zoneMap))
                .thenReturn(new FolderZoneService.ZoneResult(zone, false));
        }

        // When
        List<CodeProjectDto> result = codeProjectService.findAllByRecentFirstExcludingNonCodeZones();

        // Then - all should be excluded
        assertThat(result).isEmpty();
    }

    private CodeProject createCodeProject(String rootPath, String name) {
        return createCodeProjectWithSource(sourceId, rootPath, name);
    }

    private CodeProject createCodeProjectWithSource(UUID sourceId, String rootPath, String name) {
        return CodeProject.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .rootPath(rootPath)
            .projectType(ProjectType.NPM)
            .name(name)
            .version("1.0.0")
            .identifier(name + ":1.0.0")
            .contentHash("hash-" + name)
            .sourceFileCount(10)
            .totalFileCount(100)
            .totalSizeBytes(1000L)
            .scannedAt(Instant.now())
            .build();
    }
}
