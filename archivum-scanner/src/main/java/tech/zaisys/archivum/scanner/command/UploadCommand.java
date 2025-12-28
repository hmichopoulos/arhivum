package tech.zaisys.archivum.scanner.command;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.zaisys.archivum.scanner.service.UploadService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Upload command that sends previously generated scan output to the server.
 * Useful for large disks: scan first (multi-day), then upload separately with retry.
 */
@Slf4j
@Command(
    name = "upload",
    description = "Upload previously generated scan output to server",
    mixinStandardHelpOptions = true
)
public class UploadCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Path to scan output directory (e.g., ./output/[scan-id])"
    )
    private Path outputDir;

    @Option(
        names = {"-s", "--server-url"},
        description = "Server URL (default: http://localhost:8080)",
        defaultValue = "http://localhost:8080"
    )
    private String serverUrl;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose logging"
    )
    private boolean verbose;

    @Option(
        names = {"--timeout"},
        description = "HTTP request timeout in seconds (default: 60)",
        defaultValue = "60"
    )
    private int timeoutSeconds;

    private long startTime;

    @Override
    public Integer call() {
        try {
            startTime = System.currentTimeMillis();
            configureLogging();

            if (!validateOutputDirectory()) {
                return 1;
            }

            printHeader();
            executeUpload();
            printSummary();

            return 0;

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void executeUpload() throws Exception {
        UploadService uploadService = new UploadService(serverUrl, timeoutSeconds);
        UploadService.UploadResult result = uploadService.upload(outputDir);

        System.out.println();
        System.out.println("Upload Complete!");
        System.out.println("================");
        System.out.println("Source ID:         " + result.sourceId);
        System.out.println("Batches uploaded:  " + result.uploadedBatches);
        System.out.println("Files uploaded:    " + result.uploadedFiles);
        System.out.println("Projects uploaded: " + result.uploadedProjects);
        System.out.println("Duration:          " + formatDuration(System.currentTimeMillis() - startTime));
    }

    private void configureLogging() {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
        }
    }

    private boolean validateOutputDirectory() {
        if (!Files.exists(outputDir)) {
            System.err.println("Error: Output directory does not exist: " + outputDir);
            return false;
        }

        if (!Files.isDirectory(outputDir)) {
            System.err.println("Error: Path is not a directory: " + outputDir);
            return false;
        }

        Path sourceJson = outputDir.resolve("source.json");
        if (!Files.exists(sourceJson)) {
            System.err.println("Error: source.json not found in " + outputDir);
            return false;
        }

        return true;
    }

    private void printHeader() {
        System.out.println("Archivum Scanner - Upload Mode");
        System.out.println("==============================");
        System.out.println("Output Dir: " + outputDir.toAbsolutePath());
        System.out.println("Server URL: " + serverUrl);
        System.out.println();
    }

    private void printSummary() {
        long duration = System.currentTimeMillis() - startTime;
        System.out.println();
        System.out.println("Total Duration: " + formatDuration(duration));
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%dm %ds", minutes, remainingSeconds);
    }
}
