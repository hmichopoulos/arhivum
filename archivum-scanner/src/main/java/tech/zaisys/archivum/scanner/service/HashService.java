package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for computing SHA-256 hashes of files.
 * Uses streaming to avoid loading large files into memory.
 * Supports parallel hashing using a thread pool.
 * Supports progress callbacks for large files.
 */
@Slf4j
public class HashService implements AutoCloseable {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192; // 8 KB
    private static final long PROGRESS_INTERVAL_BYTES = 100 * 1024 * 1024; // Report every 100 MB

    /**
     * Callback interface for hash progress updates.
     * Called periodically while hashing large files.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Called when progress is made hashing a file.
         *
         * @param bytesProcessed Number of bytes processed so far
         * @param totalBytes Total size of the file
         */
        void onProgress(long bytesProcessed, long totalBytes);
    }

    private final ExecutorService executor;

    public HashService(int threads) {
        this.executor = Executors.newFixedThreadPool(threads);
        log.info("HashService initialized with {} threads", threads);
    }

    /**
     * Compute SHA-256 hash of a file synchronously.
     * Uses streaming to avoid loading the entire file into memory.
     *
     * @param file Path to the file
     * @return SHA-256 hash as lowercase hex string
     * @throws IOException if file cannot be read
     */
    public String computeHash(Path file) throws IOException {
        return computeHash(file, null);
    }

    /**
     * Compute SHA-256 hash of a file synchronously with progress callback.
     * Uses streaming to avoid loading the entire file into memory.
     * Reports progress every 100 MB for files larger than 100 MB.
     *
     * @param file Path to the file
     * @param progressCallback Optional callback for progress updates (can be null)
     * @return SHA-256 hash as lowercase hex string
     * @throws IOException if file cannot be read
     */
    public String computeHash(Path file, ProgressCallback progressCallback) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            long fileSize = Files.size(file);
            long bytesProcessed = 0;
            long lastReportedBytes = 0;

            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                    bytesProcessed += bytesRead;

                    // Report progress every PROGRESS_INTERVAL_BYTES for large files
                    if (progressCallback != null && fileSize > PROGRESS_INTERVAL_BYTES) {
                        if (bytesProcessed - lastReportedBytes >= PROGRESS_INTERVAL_BYTES) {
                            progressCallback.onProgress(bytesProcessed, fileSize);
                            lastReportedBytes = bytesProcessed;
                        }
                    }
                }

                // Final progress update
                if (progressCallback != null && fileSize > PROGRESS_INTERVAL_BYTES) {
                    progressCallback.onProgress(fileSize, fileSize);
                }
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // Should never happen for SHA-256
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Compute SHA-256 hash of a file asynchronously.
     * Returns a CompletableFuture that will contain the hash when complete.
     *
     * @param file Path to the file
     * @return CompletableFuture<String> containing the hash
     */
    public CompletableFuture<String> computeHashAsync(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return computeHash(file);
            } catch (IOException e) {
                log.error("Failed to compute hash for: {}", file, e);
                throw new RuntimeException("Failed to compute hash", e);
            }
        }, executor);
    }

    /**
     * Convert byte array to lowercase hexadecimal string.
     *
     * @param bytes Byte array
     * @return Hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Verify that a file matches a given hash.
     *
     * @param file Path to the file
     * @param expectedHash Expected SHA-256 hash
     * @return true if hashes match, false otherwise
     * @throws IOException if file cannot be read
     */
    public boolean verifyHash(Path file, String expectedHash) throws IOException {
        String actualHash = computeHash(file);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Shut down the executor service.
     * Should be called when the service is no longer needed.
     */
    @Override
    public void close() {
        executor.shutdown();
        log.info("HashService shut down");
    }
}
