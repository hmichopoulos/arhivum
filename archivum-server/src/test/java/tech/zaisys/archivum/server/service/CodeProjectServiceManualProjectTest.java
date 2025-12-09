package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.server.domain.CodeProject;
import tech.zaisys.archivum.server.repository.CodeProjectRepository;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeProjectService Manual Project Creation Tests")
class CodeProjectServiceManualProjectTest {

    @Mock
    private CodeProjectRepository repository;

    @Mock
    private FolderZoneService folderZoneService;

    @Mock
    private ScannedFileRepository scannedFileRepository;

    @InjectMocks
    private CodeProjectService codeProjectService;

    private UUID sourceId;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should create new manual code project when none exists")
    void shouldCreateNewManualCodeProject() {
        // Given
        String folderPath = "/projects/my-app";
        long fileCount = 150L;
        long totalSize = 5_000_000L;

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(fileCount);
        when(scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(totalSize);
        when(repository.save(any(CodeProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isPresent();
        CodeProjectDto project = result.get();
        assertThat(project.getRootPath()).isEqualTo(folderPath);
        assertThat(project.getIdentity().getType()).isEqualTo(ProjectType.GENERIC);
        assertThat(project.getIdentity().getName()).isEqualTo("my-app");
        assertThat(project.getIdentity().getVersion()).isEqualTo("manual");
        assertThat(project.getSourceFileCount()).isEqualTo(150);
        assertThat(project.getTotalFileCount()).isEqualTo(150);
        assertThat(project.getTotalSizeBytes()).isEqualTo(5_000_000L);

        verify(repository).findBySourceIdAndRootPath(sourceId, folderPath);
        verify(repository).save(any(CodeProject.class));
    }

    @Test
    @DisplayName("should return existing project when it already exists")
    void shouldReturnExistingProject() {
        // Given
        String folderPath = "/projects/existing-app";
        CodeProject existingProject = CodeProject.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .rootPath(folderPath)
            .projectType(ProjectType.NPM)
            .name("existing-app")
            .version("1.0.0")
            .identifier("existing-app:1.0.0")
            .contentHash("hash123")
            .sourceFileCount(100)
            .totalFileCount(200)
            .totalSizeBytes(3_000_000L)
            .scannedAt(Instant.now())
            .build();

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.of(existingProject));

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRootPath()).isEqualTo(folderPath);
        assertThat(result.get().getIdentity().getName()).isEqualTo("existing-app");

        verify(repository).findBySourceIdAndRootPath(sourceId, folderPath);
        verify(repository, never()).save(any());
        verify(scannedFileRepository, never()).countBySourceIdAndPathStartingWith(any(), any());
    }

    @Test
    @DisplayName("should return empty when folder contains no files")
    void shouldReturnEmptyWhenFolderHasNoFiles() {
        // Given
        String folderPath = "/projects/empty-folder";
        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(0L);

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isEmpty();

        verify(repository).findBySourceIdAndRootPath(sourceId, folderPath);
        verify(scannedFileRepository).countBySourceIdAndPathStartingWith(sourceId, folderPath);
        verify(repository, never()).save(any());
        verify(scannedFileRepository, never()).sumSizeBySourceIdAndPathStartingWith(any(), any());
    }

    @Test
    @DisplayName("should handle root path correctly")
    void shouldHandleRootPathCorrectly() {
        // Given
        String folderPath = "/";
        long fileCount = 10L;
        long totalSize = 1000L;

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(fileCount);
        when(scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(totalSize);
        when(repository.save(any(CodeProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getIdentity().getName()).isEqualTo("root");
    }

    @Test
    @DisplayName("should extract folder name from complex path")
    void shouldExtractFolderNameFromComplexPath() {
        // Given
        String folderPath = "/very/deep/nested/folder/structure/my-project";
        long fileCount = 50L;
        long totalSize = 2_000_000L;

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(fileCount);
        when(scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(totalSize);
        when(repository.save(any(CodeProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getIdentity().getName()).isEqualTo("my-project");
    }

    @Test
    @DisplayName("should create project with correct GENERIC type and manual version")
    void shouldCreateProjectWithCorrectTypeAndVersion() {
        // Given
        String folderPath = "/code/generic-project";
        long fileCount = 25L;
        long totalSize = 500_000L;

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(fileCount);
        when(scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(totalSize);

        ArgumentCaptor<CodeProject> projectCaptor = ArgumentCaptor.forClass(CodeProject.class);
        when(repository.save(projectCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        CodeProject savedProject = projectCaptor.getValue();
        assertThat(savedProject.getProjectType()).isEqualTo(ProjectType.GENERIC);
        assertThat(savedProject.getVersion()).isEqualTo("manual");
        assertThat(savedProject.getIdentifier()).isEqualTo(folderPath);
        // Content hash should be a valid UUID string
        assertThat(savedProject.getContentHash()).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("should handle paths with special characters")
    void shouldHandlePathsWithSpecialCharacters() {
        // Given
        String folderPath = "/projects/my app-2024_v1";
        long fileCount = 30L;
        long totalSize = 750_000L;

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(fileCount);
        when(scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(totalSize);
        when(repository.save(any(CodeProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getIdentity().getName()).isEqualTo("my app-2024_v1");
        assertThat(result.get().getRootPath()).isEqualTo(folderPath);
    }

    @Test
    @DisplayName("should handle large file counts correctly")
    void shouldHandleLargeFileCountsCorrectly() {
        // Given
        String folderPath = "/huge/project";
        long fileCount = 500_000L;  // Half a million files
        long totalSize = 50_000_000_000L;  // 50GB

        when(repository.findBySourceIdAndRootPath(sourceId, folderPath)).thenReturn(Optional.empty());
        when(scannedFileRepository.countBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(fileCount);
        when(scannedFileRepository.sumSizeBySourceIdAndPathStartingWith(sourceId, folderPath)).thenReturn(totalSize);
        when(repository.save(any(CodeProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<CodeProjectDto> result = codeProjectService.createOrGetManualCodeProject(sourceId, folderPath);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getTotalFileCount()).isEqualTo(500_000);
        assertThat(result.get().getTotalSizeBytes()).isEqualTo(50_000_000_000L);
    }
}
