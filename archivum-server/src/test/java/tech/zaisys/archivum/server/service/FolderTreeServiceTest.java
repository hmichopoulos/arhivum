package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tech.zaisys.archivum.api.dto.FolderNodeDto;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FolderTreeService.
 * Tests tree building logic and single-pass statistics calculation.
 */
@ExtendWith(MockitoExtension.class)
class FolderTreeServiceTest {

    @Mock
    private ScannedFileRepository fileRepository;

    @InjectMocks
    private FolderTreeService folderTreeService;

    private UUID sourceId;
    private List<ScannedFile> testFiles;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        testFiles = new ArrayList<>();
    }

    @Test
    void buildTree_withEmptySource_returnsRootWithNoChildren() {
        // Given: no files
        mockPaginatedResults(List.of());

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then
        assertNotNull(tree);
        assertEquals(FolderNodeDto.NodeType.FOLDER, tree.getType());
        assertEquals(0, tree.getChildren().size());
        assertEquals(0, tree.getFileCount());
        assertEquals(0L, tree.getTotalSize());
        verify(fileRepository).findBySourceId(eq(sourceId), any(Pageable.class));
    }

    @Test
    void buildTree_withFlatStructure_buildsCorrectTree() {
        // Given: files in root directory
        ScannedFile file1 = createFile("/file1.txt", 100L);
        ScannedFile file2 = createFile("/file2.txt", 200L);
        mockPaginatedResults(List.of(file1, file2));

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then
        assertNotNull(tree);
        assertEquals(2, tree.getChildren().size());
        assertEquals(2, tree.getFileCount());
        assertEquals(300L, tree.getTotalSize());

        // Verify both files are in root
        assertTrue(tree.getChildren().stream()
            .allMatch(child -> child.getType() == FolderNodeDto.NodeType.FILE));
    }

    @Test
    void buildTree_withNestedStructure_buildsHierarchy() {
        // Given: nested folder structure
        ScannedFile file1 = createFile("/photos/2023/summer/IMG_001.jpg", 1000L);
        ScannedFile file2 = createFile("/photos/2023/summer/IMG_002.jpg", 2000L);
        ScannedFile file3 = createFile("/photos/2023/winter/IMG_003.jpg", 1500L);
        ScannedFile file4 = createFile("/documents/report.pdf", 5000L);

        mockPaginatedResults(List.of(file1, file2, file3, file4));

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then
        assertNotNull(tree);
        assertEquals(2, tree.getChildren().size()); // photos and documents folders
        assertEquals(4, tree.getFileCount());
        assertEquals(9500L, tree.getTotalSize());

        // Verify photos folder
        FolderNodeDto photosFolder = findFolder(tree, "photos");
        assertNotNull(photosFolder);
        assertEquals(3, photosFolder.getFileCount());
        assertEquals(4500L, photosFolder.getTotalSize());
        assertEquals(1, photosFolder.getChildren().size()); // 2023 subfolder

        // Verify 2023 folder
        FolderNodeDto folder2023 = findFolder(photosFolder, "2023");
        assertNotNull(folder2023);
        assertEquals(3, folder2023.getFileCount());
        assertEquals(4500L, folder2023.getTotalSize());
        assertEquals(2, folder2023.getChildren().size()); // summer and winter folders

        // Verify summer folder
        FolderNodeDto summerFolder = findFolder(folder2023, "summer");
        assertNotNull(summerFolder);
        assertEquals(2, summerFolder.getFileCount());
        assertEquals(3000L, summerFolder.getTotalSize());

        // Verify documents folder
        FolderNodeDto docsFolder = findFolder(tree, "documents");
        assertNotNull(docsFolder);
        assertEquals(1, docsFolder.getFileCount());
        assertEquals(5000L, docsFolder.getTotalSize());
    }

    @Test
    void buildTree_calculatesStatsInSinglePass() {
        // Given: deep nesting to test single-pass calculation efficiency
        List<ScannedFile> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(createFile("/folder1/folder2/folder3/file" + i + ".txt", 100L));
        }
        mockPaginatedResults(files);

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then: all stats should be correctly aggregated
        assertEquals(100, tree.getFileCount());
        assertEquals(10000L, tree.getTotalSize());

        FolderNodeDto folder1 = findFolder(tree, "folder1");
        assertEquals(100, folder1.getFileCount());
        assertEquals(10000L, folder1.getTotalSize());

        FolderNodeDto folder2 = findFolder(folder1, "folder2");
        assertEquals(100, folder2.getFileCount());
        assertEquals(10000L, folder2.getTotalSize());

        FolderNodeDto folder3 = findFolder(folder2, "folder3");
        assertEquals(100, folder3.getFileCount());
        assertEquals(10000L, folder3.getTotalSize());
    }

    @Test
    void buildTree_handlesDuplicateFiles() {
        // Given: some files marked as duplicates
        ScannedFile file1 = createFile("/file1.txt", 100L, false);
        ScannedFile file2 = createFile("/file2.txt", 200L, true);
        mockPaginatedResults(List.of(file1, file2));

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then
        assertEquals(2, tree.getChildren().size());

        FolderNodeDto duplicateFile = tree.getChildren().stream()
            .filter(FolderNodeDto::getIsDuplicate)
            .findFirst()
            .orElse(null);

        assertNotNull(duplicateFile);
        assertTrue(duplicateFile.getIsDuplicate());
    }

    @Test
    void buildTree_sortsChildrenCorrectly() {
        // Given: mix of files and folders
        ScannedFile file1 = createFile("/zebra-file.txt", 100L);
        ScannedFile file2 = createFile("/alpha-file.txt", 100L);
        ScannedFile file3 = createFile("/zebra-folder/nested.txt", 100L);
        ScannedFile file4 = createFile("/alpha-folder/nested.txt", 100L);

        mockPaginatedResults(List.of(file1, file2, file3, file4));

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then: folders should come before files, alphabetically
        List<FolderNodeDto> children = tree.getChildren();
        assertEquals(4, children.size());

        // First two should be folders (sorted alphabetically)
        assertEquals(FolderNodeDto.NodeType.FOLDER, children.get(0).getType());
        assertEquals("alpha-folder", children.get(0).getName());

        assertEquals(FolderNodeDto.NodeType.FOLDER, children.get(1).getType());
        assertEquals("zebra-folder", children.get(1).getName());

        // Last two should be files (sorted alphabetically)
        assertEquals(FolderNodeDto.NodeType.FILE, children.get(2).getType());
        assertEquals("alpha-file.txt", children.get(2).getName());

        assertEquals(FolderNodeDto.NodeType.FILE, children.get(3).getType());
        assertEquals("zebra-file.txt", children.get(3).getName());
    }

    @Test
    void buildTree_handlesFileExtensions() {
        // Given: files with various extensions
        ScannedFile jpgFile = createFile("/photo.jpg", 1000L, "jpg");
        ScannedFile pdfFile = createFile("/document.pdf", 2000L, "pdf");
        ScannedFile noExtFile = createFile("/readme", 500L, null);

        mockPaginatedResults(List.of(jpgFile, pdfFile, noExtFile));

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then
        assertEquals(3, tree.getChildren().size());

        FolderNodeDto jpgNode = tree.getChildren().stream()
            .filter(n -> "photo.jpg".equals(n.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(jpgNode);
        assertEquals("jpg", jpgNode.getExtension());

        FolderNodeDto noExtNode = tree.getChildren().stream()
            .filter(n -> "readme".equals(n.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(noExtNode);
        assertNull(noExtNode.getExtension());
    }

    @Test
    void buildTree_preservesFileIds() {
        // Given: files with specific UUIDs
        UUID fileId1 = UUID.randomUUID();
        UUID fileId2 = UUID.randomUUID();
        ScannedFile file1 = createFileWithId("/file1.txt", 100L, fileId1);
        ScannedFile file2 = createFileWithId("/file2.txt", 200L, fileId2);

        mockPaginatedResults(List.of(file1, file2));

        // When
        FolderNodeDto tree = folderTreeService.buildTree(sourceId);

        // Then
        assertEquals(2, tree.getChildren().size());
        assertTrue(tree.getChildren().stream()
            .anyMatch(n -> fileId1.equals(n.getFileId())));
        assertTrue(tree.getChildren().stream()
            .anyMatch(n -> fileId2.equals(n.getFileId())));
    }

    // Helper methods

    private ScannedFile createFile(String path, long size) {
        return createFile(path, size, false);
    }

    private ScannedFile createFile(String path, long size, boolean isDuplicate) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String extension = null;
        if (fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        }
        return createFile(path, size, extension, isDuplicate);
    }

    private ScannedFile createFile(String path, long size, String extension) {
        return createFile(path, size, extension, false);
    }

    private ScannedFile createFile(String path, long size, String extension, boolean isDuplicate) {
        return createFileWithId(path, size, UUID.randomUUID(), extension, isDuplicate);
    }

    private ScannedFile createFileWithId(String path, long size, UUID fileId) {
        String extension = path.contains(".")
            ? path.substring(path.lastIndexOf('.') + 1)
            : null;
        return createFileWithId(path, size, fileId, extension, false);
    }

    private ScannedFile createFileWithId(String path, long size, UUID fileId,
                                         String extension, boolean isDuplicate) {
        ScannedFile file = new ScannedFile();
        file.setId(fileId);
        file.setPath(path);
        file.setSize(size);
        file.setExtension(extension);
        file.setIsDuplicate(isDuplicate);
        return file;
    }

    private FolderNodeDto findFolder(FolderNodeDto parent, String name) {
        return parent.getChildren().stream()
            .filter(child -> child.getType() == FolderNodeDto.NodeType.FOLDER)
            .filter(child -> name.equals(child.getName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Helper method to mock paginated repository results.
     * Simulates paginated file retrieval with a single page.
     */
    private void mockPaginatedResults(List<ScannedFile> files) {
        Page<ScannedFile> page = new PageImpl<>(files);
        when(fileRepository.findBySourceId(eq(sourceId), any(Pageable.class)))
            .thenReturn(page);
    }
}
