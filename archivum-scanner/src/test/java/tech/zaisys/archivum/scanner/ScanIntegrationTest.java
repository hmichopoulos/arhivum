package tech.zaisys.archivum.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.scanner.config.ScannerConfig;
import tech.zaisys.archivum.scanner.service.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the complete scanning workflow.
 * Verifies that all services work together correctly.
 */
class ScanIntegrationTest {

    @TempDir
    Path scanDir;

    @TempDir
    Path outputDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void fullScanWorkflow_scansDirectoryAndCreatesOutput() throws Exception {
        // Given - create test file structure
        createTestFiles();

        // Configure services
        ScannerConfig config = ScannerConfig.builder()
            .scanner(new ScannerConfig.ScannerSettings())
            .build();
        config.getScanner().setSkipSystemDirs(true);
        config.getScanner().setThreads(2);
        config.getScanner().setBatchSize(5);

        // When - perform complete scan
        SourceDto source;
        long totalFiles;

        try (HashService hashService = new HashService(2)) {
            FileWalkerService fileWalker = new FileWalkerService(config);
            MetadataService metadataService = new MetadataService();
            OutputService outputService = new OutputService(outputDir);

            // Discover files
            List<Path> files = fileWalker.walk(scanDir).files();
            totalFiles = files.size();

            // Create source
            source = SourceDto.builder()
                .id(java.util.UUID.randomUUID())
                .name("Test Source")
                .type(tech.zaisys.archivum.api.enums.SourceType.DISK)
                .rootPath(scanDir.toString())
                .status(tech.zaisys.archivum.api.enums.ScanStatus.SCANNING)
                .build();

            outputService.initializeSource(source);

            // Process files in batches
            var batch = new java.util.ArrayList<tech.zaisys.archivum.api.dto.FileDto>();
            for (Path file : files) {
                String hash = hashService.computeHash(file);
                var fileDto = metadataService.extractMetadata(file, source.getId(), hash);
                batch.add(fileDto);

                if (batch.size() >= 5) {
                    outputService.writeBatch(outputService.createBatch(source.getId(), batch));
                    batch.clear();
                }
            }

            // Write remaining files
            if (!batch.isEmpty()) {
                outputService.writeBatch(outputService.createBatch(source.getId(), batch));
            }

            // Write summary
            outputService.writeSummary(
                source.getId(),
                totalFiles,
                calculateTotalSize(files),
                List.of(),
                java.time.Instant.now()
            );
        }

        // Then - verify output structure
        Path sourceDir = outputDir.resolve(source.getId().toString());
        assertTrue(Files.exists(sourceDir), "Source directory should exist");
        assertTrue(Files.exists(sourceDir.resolve("source.json")), "source.json should exist");
        assertTrue(Files.exists(sourceDir.resolve("files")), "files directory should exist");
        assertTrue(Files.exists(sourceDir.resolve("summary.json")), "summary.json should exist");

        // Verify source metadata
        SourceDto writtenSource = objectMapper.readValue(
            sourceDir.resolve("source.json").toFile(),
            SourceDto.class
        );
        assertEquals(source.getId(), writtenSource.getId());
        assertEquals("Test Source", writtenSource.getName());

        // Verify summary
        Map<String, Object> summary = objectMapper.readValue(
            sourceDir.resolve("summary.json").toFile(),
            Map.class
        );
        assertEquals(totalFiles, ((Number) summary.get("totalFiles")).longValue());
        assertTrue(summary.containsKey("duration"));
        assertTrue(summary.containsKey("scannerVersion"));

        // Verify batches exist
        long batchCount = Files.list(sourceDir.resolve("files"))
            .filter(p -> p.getFileName().toString().startsWith("batch-"))
            .count();
        assertTrue(batchCount > 0, "At least one batch file should exist");
    }

    @Test
    void fullScanWorkflow_detectsDuplicates() throws Exception {
        // Given - create files with duplicate content
        String duplicateContent = "Duplicate content";
        Files.writeString(scanDir.resolve("file1.txt"), duplicateContent);
        Files.writeString(scanDir.resolve("file2.txt"), duplicateContent);
        Files.writeString(scanDir.resolve("file3.txt"), "Unique content");

        // When - perform scan with batch size 1 to test duplicate detection
        ScannerConfig config = ScannerConfig.builder()
            .scanner(new ScannerConfig.ScannerSettings())
            .build();

        try (HashService hashService = new HashService(2)) {
            FileWalkerService fileWalker = new FileWalkerService(config);
            MetadataService metadataService = new MetadataService();
            OutputService outputService = new OutputService(outputDir);

            List<Path> files = fileWalker.walk(scanDir).files();

            SourceDto source = SourceDto.builder()
                .id(java.util.UUID.randomUUID())
                .name("Test")
                .type(tech.zaisys.archivum.api.enums.SourceType.DISK)
                .build();

            outputService.initializeSource(source);

            // Process files one by one to enable duplicate detection
            int duplicateCount = 0;
            for (Path file : files) {
                String hash = hashService.computeHash(file);

                // Check if ALREADY exists (before registering)
                boolean isDuplicate = outputService.hashExists(hash);

                // Register hash immediately for future duplicate detection
                outputService.registerHash(hash);

                var fileDto = metadataService.extractMetadata(file, source.getId(), hash);

                // Mark as duplicate
                fileDto.setIsDuplicate(isDuplicate);
                if (isDuplicate) {
                    duplicateCount++;
                }

                // Write batch
                var batch = new java.util.ArrayList<tech.zaisys.archivum.api.dto.FileDto>();
                batch.add(fileDto);
                outputService.writeBatch(outputService.createBatch(source.getId(), batch));
            }

            // Then - verify one file is marked as duplicate
            assertEquals(1, duplicateCount, "One file should be marked as duplicate");
        }
    }

    private void createTestFiles() throws Exception {
        // Create various file types
        Files.writeString(scanDir.resolve("document.txt"), "Text content");
        Files.writeString(scanDir.resolve("photo.jpg"), "JPEG data");
        Files.writeString(scanDir.resolve("video.mp4"), "MP4 data");

        // Create subdirectory with files
        Path subdir = scanDir.resolve("subdir");
        Files.createDirectories(subdir);
        Files.writeString(subdir.resolve("nested.txt"), "Nested content");

        // Create system directory (should be skipped)
        Path trash = scanDir.resolve(".Trash");
        Files.createDirectories(trash);
        Files.writeString(trash.resolve("deleted.txt"), "Should be skipped");
    }

    private long calculateTotalSize(List<Path> files) {
        return files.stream()
            .mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (Exception e) {
                    return 0;
                }
            })
            .sum();
    }
}
