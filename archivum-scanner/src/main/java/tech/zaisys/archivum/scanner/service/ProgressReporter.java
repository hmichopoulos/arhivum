package tech.zaisys.archivum.scanner.service;

import lombok.Data;

import java.time.Duration;
import java.time.Instant;

/**
 * Reports scan progress to console.
 * Shows files processed, size, speed, current file, and ETA.
 */
public class ProgressReporter {

    private static final long UPDATE_INTERVAL_MS = 500; // Update every 500ms
    private static final long BYTES_PER_MB = 1024 * 1024;
    private static final long BYTES_PER_GB = 1024 * 1024 * 1024;

    private final ProgressState state;
    private long lastUpdateTime;

    public ProgressReporter(String sourceName, long totalFiles, long totalSize) {
        this.state = new ProgressState();
        this.state.sourceName = sourceName;
        this.state.totalFiles = totalFiles;
        this.state.totalSize = totalSize;
        this.state.startTime = Instant.now();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Update progress with current file being processed.
     *
     * @param currentFile Path of current file
     * @param fileSize Size of current file in bytes
     */
    public void updateProgress(String currentFile, long fileSize) {
        state.processedFiles++;
        state.processedSize += fileSize;
        state.currentFile = currentFile;

        // Only update display if enough time has passed
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            displayProgress();
            lastUpdateTime = currentTime;
        }
    }

    /**
     * Update progress while hashing a large file.
     * Shows within-file progress (e.g., "45.2 GB / 489 GB").
     *
     * @param currentFile Path of current file being hashed
     * @param bytesProcessed Bytes processed so far in this file
     * @param fileSize Total size of the file
     */
    public void updateFileProgress(String currentFile, long bytesProcessed, long fileSize) {
        state.currentFile = currentFile;

        // Update display
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            displayFileProgress(bytesProcessed, fileSize);
            lastUpdateTime = currentTime;
        }
    }

    /**
     * Display within-file progress for large files.
     */
    private void displayFileProgress(long bytesProcessed, long fileSize) {
        double percent = fileSize > 0 ? (bytesProcessed * 100.0 / fileSize) : 0;

        System.out.print(String.format("\rHashing: %s (%s / %s - %.1f%%)...",
            truncate(state.currentFile, 40),
            formatBytes(bytesProcessed),
            formatBytes(fileSize),
            percent));
        System.out.flush();
    }

    /**
     * Report scan completion.
     */
    public void complete() {
        state.completed = true;
        displayProgress();
        System.out.println(); // Move to next line after progress
        System.out.println();
        System.out.println("Scan complete!");
        displaySummary();
    }

    /**
     * Display current progress to console.
     * Uses single-line updates with \r for compatibility with plain console mode.
     */
    private void displayProgress() {
        double filesPercent = state.totalFiles > 0
            ? (state.processedFiles * 100.0 / state.totalFiles)
            : 0;
        double sizePercent = state.totalSize > 0
            ? (state.processedSize * 100.0 / state.totalSize)
            : 0;

        // Calculate speed
        long elapsedSeconds = Duration.between(state.startTime, Instant.now()).getSeconds();
        String speedInfo = "";
        if (elapsedSeconds > 0) {
            long filesPerSec = state.processedFiles / elapsedSeconds;
            long bytesPerSec = state.processedSize / elapsedSeconds;
            speedInfo = String.format(" @ %,d files/sec, %s/sec", filesPerSec, formatBytes(bytesPerSec));
        }

        // Single-line progress update (works in plain console mode)
        System.out.print(String.format("\rScanning: %,d/%,d files (%.1f%%) | %s/%s (%.1f%%)%s",
            state.processedFiles, state.totalFiles, filesPercent,
            formatBytes(state.processedSize), formatBytes(state.totalSize), sizePercent,
            speedInfo));
        System.out.flush();
    }

    /**
     * Display final summary.
     */
    private void displaySummary() {
        long duration = Duration.between(state.startTime, Instant.now()).getSeconds();

        System.out.printf("  Total files:     %,d%n", state.processedFiles);
        System.out.printf("  Total size:      %s%n", formatBytes(state.processedSize));
        System.out.printf("  Duration:        %s%n", formatDuration(duration));

        if (duration > 0) {
            long filesPerSec = state.processedFiles / duration;
            long bytesPerSec = state.processedSize / duration;
            System.out.printf("  Average speed:   %d files/sec, %s/sec%n",
                filesPerSec, formatBytes(bytesPerSec));
        }
    }

    /**
     * Format bytes as human-readable string.
     */
    private String formatBytes(long bytes) {
        if (bytes >= BYTES_PER_GB) {
            return String.format("%.2f GB", bytes / (double) BYTES_PER_GB);
        } else if (bytes >= BYTES_PER_MB) {
            return String.format("%.2f MB", bytes / (double) BYTES_PER_MB);
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return bytes + " B";
        }
    }

    /**
     * Format duration as HH:MM:SS.
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Truncate string if too long.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return "..." + str.substring(str.length() - maxLength + 3);
    }

    /**
     * Internal state tracking.
     */
    @Data
    private static class ProgressState {
        String sourceName;
        long totalFiles;
        long totalSize;
        long processedFiles;
        long processedSize;
        String currentFile;
        Instant startTime;
        boolean completed;
    }
}
