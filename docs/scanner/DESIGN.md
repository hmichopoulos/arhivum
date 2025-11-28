# Scanner Design

## Architecture Overview

The scanner follows a **pipeline architecture** where files flow through processing stages:

```
User Input → Physical ID Detection → File Walking → Hashing → Metadata Extraction → Batching → Server/Storage
```

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         ScannerApp (CLI)                        │
│                         (Picocli)                               │
└────────────┬────────────────────────────────────────────────────┘
             │
             ├─ ScanCommand
             ├─ OpenArchiveCommand
             ├─ CopyCommand
             ├─ StatusCommand
             └─ ConfigCommand
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     ScanOrchestrator                            │
│  (coordinates all components, manages lifecycle)                │
└────┬──────────────────────────────────────────────┬─────────────┘
     │                                               │
     ▼                                               ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ PhysicalId      │  │ Archive         │  │ Interactive     │
│ Detector        │  │ Detector        │  │ Prompt          │
└─────────────────┘  └─────────────────┘  └─────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SourceHierarchyService                         │
│  (manages parent-child relationships)                           │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                       FileWalker                                │
│  (recursive directory traversal)                                │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      HashService                                │
│  (parallel SHA-256 computation)                                 │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                   MetadataExtractor                             │
│  (EXIF, MIME type, file attributes)                             │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      OutputService                              │
│  ├─ ServerOutput (REST API)                                     │
│  └─ JsonOutput (dry-run)                                        │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      CopyService                                │
│  (optional copy to staging)                                     │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. ScanOrchestrator

**Responsibility:** Coordinates the entire scan process.

**Key Methods:**
```java
public class ScanOrchestrator {
    public ScanResult executeScan(ScanRequest request);
    private SourceDto prepareSource(ScanRequest request);
    private void walkAndProcess(SourceDto source, Path rootPath);
    private void completeScan(SourceDto source, ScanStats stats);
}
```

**Flow:**
1. Detect physical IDs
2. Prompt user for source details
3. Create source hierarchy
4. Walk file system
5. Process files through pipeline
6. Report completion

### 2. PhysicalIdDetector

**Responsibility:** Auto-detect physical device identifiers.

**Detection Methods:**
- **Linux:** `blkid`, `lsblk`, `/sys/block/`, `udevadm`
- **macOS:** `diskutil info`, `system_profiler`
- **Windows:** `wmic`, `fsutil`, PowerShell

**Key Methods:**
```java
public class PhysicalIdDetector {
    public PhysicalId detect(Path mountPoint);
    private String detectDiskUuid(Path mountPoint);
    private String detectPartitionUuid(Path mountPoint);
    private String detectSerialNumber(Path device);
    private String detectFilesystemType(Path mountPoint);
    private SpaceInfo getSpaceInfo(Path mountPoint);
}
```

### 3. ArchiveDetector

**Responsibility:** Identify archive files that might be postponed.

**Detection Logic:**
```java
public class ArchiveDetector {
    public boolean isArchive(Path file);
    public SourceType detectArchiveType(Path file);
    public boolean shouldPromptForPostpone(Path file, long size);
}
```

**Archive Extensions:**
- `.tar`, `.tar.gz`, `.tgz`, `.tar.bz2`, `.tbz`
- `.zip`, `.7z`, `.rar`
- `.iso`, `.img`, `.dmg`
- `.tib`, `.bak` (Acronis)

**Prompt Threshold:**
- Size > 100 MB
- Known backup extensions (.tib, .bak)
- User can configure threshold

### 4. InteractivePrompt

**Responsibility:** Collect user input with smart defaults.

**Key Methods:**
```java
public class InteractivePrompt {
    public String promptForName(String suggestedName);
    public String promptForPhysicalLabel(String volumeLabel);
    public String promptForNotes();
    public PostponeDecision promptForArchive(Path archive, long size);
}
```

**Smart Defaults:**
```java
// Volume label: "Backup_2018"
// Suggested name: "Backup-2018"

// Physical label prompt:
// "Enter physical label (sticker on disk) [leave empty if none]: "
```

### 5. SourceHierarchyService

**Responsibility:** Manage parent-child source relationships.

**Key Methods:**
```java
public class SourceHierarchyService {
    public SourceDto createDiskSource(PhysicalId physicalId, String name);
    public SourceDto createPartitionSource(SourceDto parent, PhysicalId physicalId);
    public SourceDto createArchiveSource(SourceDto parent, Path archivePath, boolean postponed);
    public void linkParentChild(UUID parentId, UUID childId);
}
```

**Hierarchy Examples:**

**Example 1: Simple disk scan**
```
Disk "WD-4TB-Black" (UUID: abc123)
  └─ Partition "Main" (UUID: def456)
       └─ Files...
```

**Example 2: Disk with postponed archives**
```
Disk "Seagate-2TB" (UUID: xyz789)
  └─ Partition "Backups" (UUID: uvw456)
       ├─ Archive "photos-2017.tar.gz" (postponed)
       ├─ Archive "documents.zip" (postponed)
       └─ Normal files...
```

### 6. FileWalker

**Responsibility:** Recursively traverse directory tree.

**Key Methods:**
```java
public class FileWalker {
    public Stream<Path> walk(Path rootPath, WalkOptions options);
    private boolean shouldSkip(Path path);
    private boolean isSystemDirectory(Path path);
}
```

**Skip Patterns:**
- `.Trash`, `.Trashes`
- `$RECYCLE.BIN`
- `System Volume Information`
- `.DS_Store` files
- Configurable user patterns

**Options:**
```java
public record WalkOptions(
    boolean followSymlinks,
    int maxDepth,
    Set<String> skipPatterns
) {}
```

### 7. HashService

**Responsibility:** Compute SHA-256 hashes in parallel.

**Key Methods:**
```java
public class HashService {
    public String computeHash(Path file);
    public CompletableFuture<String> computeHashAsync(Path file);
    public Map<Path, String> computeHashes(List<Path> files);
}
```

**Implementation:**
```java
public class HashService {
    private final ExecutorService executor;

    public String computeHash(Path file) throws IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return bytesToHex(digest.digest());
    }
}
```

**Performance:**
- Thread pool size: `Runtime.getRuntime().availableProcessors()`
- Buffer size: 8 KB (optimal for most disks)
- Parallel hashing for multiple files

### 8. MetadataExtractor

**Responsibility:** Extract file metadata, especially EXIF.

**Key Methods:**
```java
public class MetadataExtractor {
    public FileDto extractMetadata(Path file, String hash);
    public ExifMetadata extractExif(Path file);
    private boolean shouldExtractExif(String extension);
    public String detectMimeType(Path file);
}
```

**EXIF Extraction:**
```java
public class MetadataExtractor {
    public ExifMetadata extractExif(Path file) {
        Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

        ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

        return ExifMetadata.builder()
            .cameraMake(getCameraMake(metadata))
            .cameraModel(getCameraModel(metadata))
            .dateTimeOriginal(getDateTimeOriginal(exifDir))
            .gps(extractGps(gpsDir))
            // ... more fields
            .build();
    }
}
```

**Library:** Drew Noakes' `metadata-extractor`

### 9. OutputService

**Responsibility:** Send results to server or write to JSON.

**Interface:**
```java
public interface OutputService {
    void createSource(SourceDto source);
    Map<String, Boolean> checkHashes(List<String> hashes);
    void sendFileBatch(FileBatchDto batch);
    void completeScan(CompleteScanRequest request);
}
```

**Implementations:**

**ServerOutput:**
```java
public class ServerOutput implements OutputService {
    private final OkHttpClient httpClient;
    private final String serverUrl;
    private final String apiKey;

    @Override
    public void sendFileBatch(FileBatchDto batch) {
        String json = objectMapper.writeValueAsString(batch);
        Request request = new Request.Builder()
            .url(serverUrl + "/api/scan/files")
            .header("X-API-Key", apiKey)
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
        }
    }
}
```

**JsonOutput (dry-run):**
```java
public class JsonOutput implements OutputService {
    private final Path outputDir;
    private final Set<String> knownHashes = new HashSet<>();

    @Override
    public void sendFileBatch(FileBatchDto batch) {
        Path batchFile = outputDir.resolve("batch-" + batch.getBatchNumber() + ".json");
        objectMapper.writeValue(batchFile.toFile(), batch);

        // Track hashes locally
        batch.getFiles().forEach(f -> knownHashes.add(f.getSha256()));
    }

    @Override
    public Map<String, Boolean> checkHashes(List<String> hashes) {
        return hashes.stream()
            .collect(Collectors.toMap(h -> h, knownHashes::contains));
    }
}
```

### 10. CopyService

**Responsibility:** Copy files to staging area with verification.

**Key Methods:**
```java
public class CopyService {
    public void copy(FileDto file, Path targetDir);
    private void copyWithVerification(Path source, Path target);
    private void verifyHash(Path file, String expectedHash);
}
```

**Implementation:**
```java
public void copy(FileDto file, Path targetDir) {
    Path sourcePath = Path.of(file.getPath());
    Path targetPath = targetDir.resolve(file.getSourceId().toString())
                               .resolve(file.getPath());

    Files.createDirectories(targetPath.getParent());
    Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);

    // Verify hash
    String copiedHash = hashService.computeHash(targetPath);
    if (!copiedHash.equals(file.getSha256())) {
        Files.delete(targetPath);
        throw new IOException("Hash mismatch after copy");
    }
}
```

### 11. ProgressReporter

**Responsibility:** Report progress to console.

**Key Methods:**
```java
public class ProgressReporter {
    public void reportProgress(ScanProgress progress);
    public void reportFile(String filename, long size);
    public void reportCompletion(ScanStats stats);
}
```

**Output Format:**
```
Scanning: /media/disk1
Source: Backup-2018 (PARTITION)
Files: 1,234 / 12,345 (10%)
Size: 1.2 GB / 12.3 GB (10%)
Speed: 120 files/sec, 45 MB/sec
Hashing: IMG_1234.jpg (2.4 MB)
```

## Data Flow

### Scan Flow

```
1. User runs: archivum scan /media/disk1 --name "Backup-2018"

2. PhysicalIdDetector detects:
   - Disk UUID: abc123
   - Partition UUID: def456
   - Volume Label: "Backup_2018"
   - Filesystem: ext4

3. InteractivePrompt asks:
   - Name: [Backup-2018] ✓
   - Physical label: [user enters "WD-Black-4TB"]
   - Notes: [leave empty]

4. SourceHierarchyService creates:
   - Disk source (parent)
   - Partition source (child)

5. OutputService creates source on server (or stores locally)

6. FileWalker traverses /media/disk1:
   - Skip .Trash, $RECYCLE.BIN
   - Detect archive: photos-2017.tar.gz (2.3 GB)

7. InteractivePrompt asks:
   - Found archive photos-2017.tar.gz (2.3 GB)
   - Scan contents now? [y/N/postpone]: postpone

8. ArchiveDetector catalogs:
   - Create archive source (postponed)
   - Compute hash of archive file
   - Link to partition source

9. For each regular file:
   a. HashService computes SHA-256
   b. OutputService checks if hash exists
   c. If new hash:
      - MetadataExtractor extracts EXIF
   d. If existing hash:
      - Skip EXIF (optimization)
   e. Add to batch

10. Every 1000 files:
    - OutputService sends batch to server

11. After all files:
    - OutputService sends completion
    - ProgressReporter shows summary
```

### Open Archive Flow

```
1. User runs: archivum open-archive <archive-source-id>

2. System retrieves archive source:
   - Get parent partition
   - Get archive file path

3. Extract to temporary directory

4. Run normal scan on extracted contents:
   - Create child source under archive
   - Walk, hash, extract metadata
   - Send to server

5. Clean up temporary extraction
```

## Configuration

### Config File Structure

```yaml
server:
  url: https://archivum.local:8080
  apiKey: ${ARCHIVUM_API_KEY}
  timeout: 30s

scanner:
  threads: 8
  batchSize: 1000
  skipSystemDirs: true
  followSymlinks: false
  skipPatterns:
    - ".Trash"
    - "$RECYCLE.BIN"
    - "node_modules"

copy:
  enabled: false
  target: /mnt/nas/Archivum/Staging/
  verify: true
  overwrite: false

dryRun:
  enabled: false
  outputDir: ~/.local/share/archivum/scans/

metadata:
  extractExif: true
  detectMimeType: true

archive:
  promptThreshold: 104857600  # 100 MB
  autoPostpone:
    - "*.tib"
    - "*.vhdx"
```

### CLI Overrides

```bash
archivum scan /media/disk1 \
  --name "Backup-2018" \
  --physical-label "WD-Black-4TB" \
  --threads 16 \
  --batch-size 500 \
  --dry-run \
  --output-dir ./scan-results/ \
  --copy-to /mnt/nas/Staging/ \
  --skip-pattern "*.tmp" \
  --follow-symlinks
```

## Error Handling

### Network Errors

```java
public void sendBatchWithRetry(FileBatchDto batch) {
    int maxRetries = 3;
    long delay = 1000; // 1 second

    for (int i = 0; i < maxRetries; i++) {
        try {
            sendFileBatch(batch);
            return;
        } catch (IOException e) {
            if (i == maxRetries - 1) throw e;
            Thread.sleep(delay * (i + 1)); // exponential backoff
        }
    }
}
```

### File Access Errors

```java
public void walkWithErrorHandling(Path root) {
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            log.warn("Cannot access: {} - {}", file, exc.getMessage());
            return FileVisitResult.CONTINUE; // Skip and continue
        }
    });
}
```

### Hash Computation Errors

```java
public Optional<FileDto> processFile(Path file) {
    try {
        String hash = hashService.computeHash(file);
        // ... continue processing
    } catch (IOException e) {
        log.error("Cannot hash: {} - {}", file, e.getMessage());
        return Optional.empty(); // Skip this file
    }
}
```

## Testing Strategy

### Unit Tests

```java
@Test
void testHashComputation() {
    Path testFile = createTestFile("test.txt", "hello world");
    String hash = hashService.computeHash(testFile);
    assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash);
}

@Test
void testExifExtraction() {
    Path testImage = loadTestImage("sample.jpg");
    ExifMetadata exif = metadataExtractor.extractExif(testImage);
    assertEquals("Apple", exif.getCameraMake());
    assertEquals("iPhone 13 Pro", exif.getCameraModel());
}
```

### Integration Tests

```java
@Test
void testFullScanFlow() {
    // Create test directory with files
    Path testDir = createTestDirectory();

    // Run scan
    ScanRequest request = ScanRequest.builder()
        .rootPath(testDir)
        .name("Test Scan")
        .dryRun(true)
        .build();

    ScanResult result = orchestrator.executeScan(request);

    // Verify results
    assertEquals(10, result.getTotalFiles());
    assertTrue(Files.exists(Path.of("scan-results/batch-1.json")));
}
```

## Performance Considerations

### Parallel Hashing

- Use `ForkJoinPool` for work-stealing
- Optimal thread count: CPU cores
- Large files (>100MB): hash in foreground
- Small files: batch and parallelize

### Memory Management

- Stream files, don't load into memory
- Use fixed-size thread pools
- Limit batch size to control memory
- Release resources promptly

### Network Optimization

- Batch requests (1000 files per batch)
- Compress JSON payloads
- Reuse HTTP connections
- Pipeline requests when possible

## Security Considerations

### API Authentication

```java
Request request = new Request.Builder()
    .url(serverUrl + "/api/scan/files")
    .header("X-API-Key", apiKey)
    .post(body)
    .build();
```

### File System Access

- Respect file permissions
- Don't follow symlinks by default
- Validate paths (no path traversal)
- Handle permission errors gracefully

## Future Enhancements

- **Cloud scanning:** Google Drive, OneDrive, Dropbox
- **SSH scanning:** Remote file systems via SFTP
- **Incremental scanning:** Only scan new/modified files
- **Parallel archive extraction:** Process multiple archives concurrently
- **Advanced filters:** Regex patterns, size ranges, date ranges
- **Video/audio metadata:** Extract metadata from media files
