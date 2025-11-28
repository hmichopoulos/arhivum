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
     * Report scan completion.
     */
    public void complete() {
        state.completed = true;
        displayProgress();
        System.out.println();
        System.out.println("Scan complete!");
        displaySummary();
    }

    /**
     * Display current progress to console.
     */
    private void displayProgress() {
        // Clear previous lines (ANSI escape codes)
        if (state.processedFiles > 1) {
            System.out.print("\033[6A\033[J"); // Move up 6 lines and clear to end
        }

        System.out.println("Scanning: " + state.sourceName);
        System.out.println();

        // Progress
        double filesPercent = state.totalFiles > 0
            ? (state.processedFiles * 100.0 / state.totalFiles)
            : 0;
        double sizePercent = state.totalSize > 0
            ? (state.processedSize * 100.0 / state.totalSize)
            : 0;

        System.out.printf("Progress:%n");
        System.out.printf("  Files:  %,d / %,d (%.1f%%)%n",
            state.processedFiles, state.totalFiles, filesPercent);
        System.out.printf("  Size:   %s / %s (%.1f%%)%n",
            formatBytes(state.processedSize),
            formatBytes(state.totalSize),
            sizePercent);

        // Speed
        long elapsedSeconds = Duration.between(state.startTime, Instant.now()).getSeconds();
        if (elapsedSeconds > 0) {
            long filesPerSec = state.processedFiles / elapsedSeconds;
            long bytesPerSec = state.processedSize / elapsedSeconds;
            System.out.printf("  Speed:  %d files/sec, %s/sec%n",
                filesPerSec, formatBytes(bytesPerSec));
        }

        // Current file
        System.out.println();
        System.out.println("Current: " + truncate(state.currentFile, 60));
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
