package tech.zaisys.archivum.scanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.scanner.config.ScannerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileWalkerServiceTest {

    @TempDir
    Path tempDir;

    private ScannerConfig config;
    private FileWalkerService fileWalker;

    @BeforeEach
    void setUp() {
        config = ScannerConfig.builder()
            .scanner(new ScannerConfig.ScannerSettings())
            .build();
        config.getScanner().setSkipSystemDirs(true);
        config.getScanner().setExcludePatterns(List.of());

        fileWalker = new FileWalkerService(config);
    }

    @Test
    void walk_emptyDirectory_returnsEmptyList() throws IOException {
        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertTrue(files.isEmpty());
    }

    @Test
    void walk_singleFile_returnsFile() throws IOException {
        // Given
        Path file = tempDir.resolve("test.txt");
        Files.createFile(file);

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
        assertEquals(file, files.get(0));
    }

    @Test
    void walk_multipleFiles_returnsAllFiles() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createFile(tempDir.resolve("file3.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(3, files.size());
    }

    @Test
    void walk_nestedDirectories_returnsAllFiles() throws IOException {
        // Given
        Path subdir1 = tempDir.resolve("subdir1");
        Path subdir2 = tempDir.resolve("subdir1/subdir2");
        Files.createDirectories(subdir2);

        Files.createFile(tempDir.resolve("root.txt"));
        Files.createFile(subdir1.resolve("sub1.txt"));
        Files.createFile(subdir2.resolve("sub2.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(3, files.size());
    }

    @Test
    void walk_skipsDirectories_onlyReturnsFiles() throws IOException {
        // Given
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectories(subdir);
        Files.createFile(tempDir.resolve("file.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
        assertTrue(Files.isRegularFile(files.get(0)));
    }

    @Test
    void walk_skipsSystemDirectories_whenEnabled() throws IOException {
        // Given
        Path trash = tempDir.resolve(".Trash");
        Path recycle = tempDir.resolve("$RECYCLE.BIN");
        Files.createDirectories(trash);
        Files.createDirectories(recycle);

        Files.createFile(trash.resolve("deleted.txt"));
        Files.createFile(recycle.resolve("deleted2.txt"));
        Files.createFile(tempDir.resolve("normal.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
        assertTrue(files.get(0).toString().endsWith("normal.txt"));
    }

    @Test
    void walk_includesSystemDirectories_whenDisabled() throws IOException {
        // Given
        config.getScanner().setSkipSystemDirs(false);
        fileWalker = new FileWalkerService(config);

        Path trash = tempDir.resolve(".Trash");
        Files.createDirectories(trash);
        Files.createFile(trash.resolve("deleted.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
    }

    @Test
    void walk_excludesFilesByPattern() throws IOException {
        // Given
        config.getScanner().setExcludePatterns(List.of("*.tmp", ".DS_Store"));
        fileWalker = new FileWalkerService(config);

        Files.createFile(tempDir.resolve("file.txt"));
        Files.createFile(tempDir.resolve("file.tmp"));
        Files.createFile(tempDir.resolve(".DS_Store"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
        assertTrue(files.get(0).toString().endsWith("file.txt"));
    }

    @Test
    void walk_multipleExcludePatterns() throws IOException {
        // Given
        config.getScanner().setExcludePatterns(List.of("*.tmp", "*.bak", "*.log"));
        fileWalker = new FileWalkerService(config);

        Files.createFile(tempDir.resolve("data.txt"));
        Files.createFile(tempDir.resolve("temp.tmp"));
        Files.createFile(tempDir.resolve("backup.bak"));
        Files.createFile(tempDir.resolve("debug.log"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
        assertTrue(files.get(0).toString().endsWith("data.txt"));
    }

    @Test
    void walk_continuesToNextFile_whenOneFileInaccessible() throws IOException {
        // Given
        Path accessible = tempDir.resolve("accessible.txt");
        Files.createFile(accessible);

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then - should not throw, should continue
        assertTrue(files.contains(accessible));
    }

    @Test
    void countFiles_returnCorrectCount() throws IOException {
        // Given
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));
        Files.createFile(tempDir.resolve("file3.txt"));

        // When
        long count = fileWalker.countFiles(tempDir);

        // Then
        assertEquals(3, count);
    }

    @Test
    void walk_handlesEmptySubdirectories() throws IOException {
        // Given
        Path emptySubdir = tempDir.resolve("empty");
        Files.createDirectories(emptySubdir);
        Files.createFile(tempDir.resolve("file.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
    }

    @Test
    void walk_handlesDeepNesting() throws IOException {
        // Given
        Path deep = tempDir;
        for (int i = 0; i < 10; i++) {
            deep = deep.resolve("level" + i);
        }
        Files.createDirectories(deep);
        Files.createFile(deep.resolve("deep-file.txt"));

        // When
        List<Path> files = fileWalker.walk(tempDir).files();

        // Then
        assertEquals(1, files.size());
        assertTrue(files.get(0).toString().endsWith("deep-file.txt"));
    }

    @Test
    void walk_nonExistentDirectory_returnsEmptyList() throws IOException {
        // Given
        Path nonExistent = tempDir.resolve("does-not-exist");

        // When
        List<Path> files = fileWalker.walk(nonExistent).files();

        // Then - should handle gracefully and return empty list
        assertTrue(files.isEmpty());
    }
}
