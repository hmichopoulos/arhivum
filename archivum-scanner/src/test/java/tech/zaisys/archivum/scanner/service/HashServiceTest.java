package tech.zaisys.archivum.scanner.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class HashServiceTest {

    private HashService hashService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        hashService = new HashService(2);
    }

    @AfterEach
    void tearDown() {
        hashService.close();
    }

    @Test
    void computeHash_emptyFile_returnsExpectedHash() throws IOException {
        // Given
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        // When
        String hash = hashService.computeHash(emptyFile);

        // Then
        // SHA-256 of empty file
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void computeHash_smallFile_returnsCorrectHash() throws IOException {
        // Given
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        // When
        String hash = hashService.computeHash(file);

        // Then
        // SHA-256 of "Hello, World!"
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash);
    }

    @Test
    void computeHash_largeFile_returnsCorrectHash() throws IOException {
        // Given
        Path file = tempDir.resolve("large.txt");
        // Create file larger than buffer size (8KB)
        String content = "a".repeat(10000);
        Files.writeString(file, content);

        // When
        String hash = hashService.computeHash(file);

        // Then
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 is 64 hex characters
    }

    @Test
    void computeHash_sameContent_returnsSameHash() throws IOException {
        // Given
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        String content = "Identical content";
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        // When
        String hash1 = hashService.computeHash(file1);
        String hash2 = hashService.computeHash(file2);

        // Then
        assertEquals(hash1, hash2);
    }

    @Test
    void computeHash_differentContent_returnsDifferentHash() throws IOException {
        // Given
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "Content A");
        Files.writeString(file2, "Content B");

        // When
        String hash1 = hashService.computeHash(file1);
        String hash2 = hashService.computeHash(file2);

        // Then
        assertNotEquals(hash1, hash2);
    }

    @Test
    void computeHashAsync_returnsCompletableFuture() throws Exception {
        // Given
        Path file = tempDir.resolve("async.txt");
        Files.writeString(file, "Async test");

        // When
        CompletableFuture<String> future = hashService.computeHashAsync(file);
        String hash = future.get();

        // Then
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void verifyHash_correctHash_returnsTrue() throws IOException {
        // Given
        Path file = tempDir.resolve("verify.txt");
        Files.writeString(file, "Test content");
        String expectedHash = hashService.computeHash(file);

        // When
        boolean result = hashService.verifyHash(file, expectedHash);

        // Then
        assertTrue(result);
    }

    @Test
    void verifyHash_incorrectHash_returnsFalse() throws IOException {
        // Given
        Path file = tempDir.resolve("verify.txt");
        Files.writeString(file, "Test content");
        String wrongHash = "0".repeat(64);

        // When
        boolean result = hashService.verifyHash(file, wrongHash);

        // Then
        assertFalse(result);
    }

    @Test
    void computeHash_nonExistentFile_throwsIOException() {
        // Given
        Path nonExistent = tempDir.resolve("does-not-exist.txt");

        // When & Then
        assertThrows(IOException.class, () -> hashService.computeHash(nonExistent));
    }
}
