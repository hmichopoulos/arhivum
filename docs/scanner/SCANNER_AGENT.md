# Scanner Agent - Detailed Specification

This document provides complete implementation details for the archivum-scanner CLI tool. Use this as the primary reference during development.

## Command-Line Interface

### Main Commands

```bash
archivum-scanner <command> [options]

Commands:
  scan <path>           Scan a directory or device
  open-archive <id>     Open and scan a postponed archive
  copy <source-id>      Copy scanned files to staging
  status [source-id]    Show scan status
  config <action>       Manage configuration

Options:
  --help, -h           Show help
  --version, -v        Show version
```

### `scan` Command

```bash
archivum-scanner scan <path> [options]

Arguments:
  <path>               Path to scan (directory or device mount point)

Options:
  --name NAME          Logical name for this source
  --physical-label LABEL  Physical sticker label
  --notes TEXT         Notes about this source
  --threads N          Number of hash worker threads (default: CPU count)
  --batch-size N       Files per batch (default: 1000)
  --copy-to PATH       Copy files to staging directory
  --dry-run            Don't communicate with server (default: true for MVP)
  --output-dir PATH    Output directory for dry-run (default: ~/.archivum/scans/)
  --exclude PATTERN    Exclude pattern (can be repeated)
  --skip-system-dirs   Skip system directories (default: true)
  --follow-symlinks    Follow symbolic links (default: false)
  --auto-postpone      Automatically postpone all archives (default: false)
  --config FILE        Config file path (default: ~/.config/archivum/scanner.yml)
  --verbose, -v        Verbose output
  --quiet, -q          Quiet mode (errors only)

Examples:
  # Basic scan
  archivum-scanner scan /media/disk1

  # Scan with custom name and copy to staging
  archivum-scanner scan /dev/sdb1 \\
    --name "Dad-Laptop-2018" \\
    --physical-label "WD #17" \\
    --copy-to /mnt/nas/Staging/

  # Scan with custom exclusions
  archivum-scanner scan /backup \\
    --exclude "*.tmp" \\
    --exclude "*.log" \\
    --threads 16
```

### `open-archive` Command

```bash
archivum-scanner open-archive <source-id> [options]

Arguments:
  <source-id>          Source ID of postponed archive (UUID or name)

Options:
  --mount PATH         Mount point for extracted archive (default: /tmp/archivum/)
  --keep-extracted     Don't delete extracted files after scan
  --threads N          Number of hash worker threads
  --batch-size N       Files per batch
  --copy-to PATH       Copy files to staging
  --config FILE        Config file path

Examples:
  # Open archive by ID
  archivum-scanner open-archive 550e8400-e29b-41d4-a716-446655440000

  # Open archive by name
  archivum-scanner open-archive "dad-photos-2015.tar"
```

### `copy` Command

```bash
archivum-scanner copy <source-id> <target> [options]

Arguments:
  <source-id>          Source ID to copy (UUID or name)
  <target>             Target directory

Options:
  --verify             Verify hashes after copy (default: true)
  --skip-existing      Skip files that already exist
  --threads N          Number of copy worker threads (default: 4)
  --flatten            Flatten directory structure
  --config FILE        Config file path

Examples:
  # Copy with verification
  archivum-scanner copy 550e8400-e29b-41d4-a716-446655440000 /mnt/nas/Staging/

  # Copy existing scan (by name) without verification
  archivum-scanner copy "Dad-Laptop-2018" /staging --no-verify
```

### `status` Command

```bash
archivum-scanner status [source-id] [options]

Arguments:
  [source-id]          Source ID to check (optional, shows all if omitted)

Options:
  --hierarchy          Show source hierarchy tree
  --json               Output as JSON
  --config FILE        Config file path

Examples:
  # Show all sources
  archivum-scanner status

  # Show specific source with hierarchy
  archivum-scanner status 550e8400-e29b-41d4-a716-446655440000 --hierarchy
```

### `config` Command

```bash
archivum-scanner config <action> [options]

Actions:
  init                 Create default config file
  show                 Show current configuration
  test                 Test server connection

Options:
  --file PATH          Config file path

Examples:
  # Initialize config
  archivum-scanner config init

  # Show current config
  archivum-scanner config show

  # Test server connection
  archivum-scanner config test
```

## Configuration

### Configuration File

**Location:** `~/.config/archivum/scanner.yml`

```yaml
# Server connection (not used in MVP dry-run mode)
server:
  url: "https://archivum.local:8080"
  apiKey: "${ARCHIVUM_API_KEY}"
  timeout: 30s
  retries: 3

# Scanner settings
scanner:
  # Hash computation
  threads: 0  # 0 = auto-detect CPU count
  batchSize: 1000

  # File walking
  skipSystemDirs: true
  followSymlinks: false
  excludePatterns:
    - ".Trash"
    - ".Trashes"
    - "$RECYCLE.BIN"
    - "System Volume Information"
    - ".DS_Store"
    - "*.tmp"
    - "*.temp"

  # Archive handling
  archivePromptThreshold: 104857600  # 100 MB
  autoPostponePatterns:
    - "*.tib"
    - "*.vhdx"
    - "*.vmdk"

# Copy settings
copy:
  enabled: false
  threads: 4
  verify: true
  skipExisting: false
  flatten: false

# Dry-run mode (MVP default)
dryRun:
  enabled: true
  outputDir: "~/.archivum/scans/"

# Metadata extraction
metadata:
  extractExif: true
  detectMimeType: true
  exifOptimization: true  # Skip EXIF for duplicate hashes

# Logging
logging:
  level: INFO  # DEBUG, INFO, WARN, ERROR
  file: "~/.archivum/scanner.log"
  console: true
```

### Environment Variables

```bash
# Override config file location
ARCHIVUM_CONFIG=/path/to/config.yml

# Server API key
ARCHIVUM_API_KEY=your-api-key-here

# Dry-run output directory
ARCHIVUM_OUTPUT_DIR=/custom/output/dir
```

### CLI Overrides

CLI options override config file and environment variables:

**Priority (highest to lowest):**
1. CLI options (`--threads 16`)
2. Environment variables (`ARCHIVUM_CONFIG`)
3. Config file (`~/.config/archivum/scanner.yml`)
4. Built-in defaults

## Scan Workflow

### 1. Initialization

```
1. Load configuration (file → env vars → CLI options)
2. Validate scan path exists and is accessible
3. Detect source type (DISK, PARTITION, LVM_VOLUME)
4. Detect physical identifiers
5. Interactive prompts for user input
6. Create source hierarchy
7. Initialize output service (JSON or API client)
```

### 2. Physical ID Detection

**For Linux:**
```bash
# Disk UUID
lsblk -o NAME,UUID,FSTYPE,SIZE /dev/sdb1

# Partition info
blkid /dev/sdb1

# Serial number
udevadm info --query=property --name=/dev/sdb | grep ID_SERIAL

# Volume label
lsblk -o LABEL /dev/sdb1

# Filesystem type
df -T /mount/point

# Capacity/used space
df -B1 /mount/point
```

**For macOS:**
```bash
# Disk info
diskutil info /dev/disk2s1

# Volume UUID
diskutil info /Volumes/MyDisk | grep UUID

# Serial number
system_profiler SPSerialATADataType
```

**For Windows:**
```powershell
# Volume info
Get-Volume

# Disk serial
wmic diskdrive get serialnumber

# Partition UUID
wmic path win32_volume get deviceid
```

### 3. Interactive Prompts

**Source Name:**
```
Detected Volume: "BACKUP_2018"
Enter logical name [BACKUP_2018]: ▋
```

Smart default: Volume label with underscores replaced by hyphens.

**Physical Label:**
```
Enter physical label (sticker on disk) [leave empty if none]: ▋
```

Optional field. User can skip.

**Notes:**
```
Enter notes (optional): ▋
```

Optional free-form text.

### 4. Archive Detection

**When scanning, for each file:**

```
If file extension in [.tar, .tar.gz, .tgz, .zip, .7z, .rar, .iso, .img, .tib, .bak]:
  If file size > archivePromptThreshold (default 100MB):
    Prompt user:
      Found archive: {filename} ({size})
      [S]can contents / [P]ostpone / [A]uto-postpone all / [I]gnore: ▋

    If user selects:
      S (Scan):     Extract and scan contents now
      P (Postpone): Create archive source with postponed=true, hash archive file
      A (Auto):     Set autoPostpone=true for remainder of scan
      I (Ignore):   Treat as regular file
```

### 5. File Processing Pipeline

```
For each file:
  1. Extract basic metadata (name, size, dates, permissions)
  2. Compute SHA-256 hash (streaming, don't load into memory)
  3. If file is image:
       a. Check hash cache (has it been seen before?)
       b. If new hash: Extract EXIF metadata
       c. If duplicate hash: Skip EXIF extraction
  4. Add to current batch
  5. If batch full (default 1000 files):
       a. Write batch to JSON file
       b. Clear batch
       c. Report progress
  6. If copy-to enabled:
       a. Copy file to staging directory
       b. Verify hash if enabled
```

### 6. Progress Reporting

**Console Output:**

```
Scanning: /media/disk1/Dad-Laptop-2018
Source: Dad-Laptop-2018 (PARTITION)

Progress:
  Files:    1,234 / 12,345 (10.0%)
  Size:     1.2 GB / 12.3 GB (9.8%)
  Speed:    120 files/sec, 45 MB/sec
  Elapsed:  00:02:15
  ETA:      00:20:15

Current:
  Hashing: /photos/2018/IMG_1234.jpg (2.4 MB)

Archives:
  Postponed: 3 archives (75 GB)

Errors:
  Skipped: 2 files (permission denied)
```

Update every 500ms or every 100 files, whichever is less frequent.

### 7. Completion

```
1. Write final batch (if any files remaining)
2. Write summary.json
3. Report completion statistics
4. If copy-to enabled: Show copy statistics
5. Exit with appropriate code (0 = success, 1 = errors)
```

## Data Structures

### Source JSON (`source.json`)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Dad-Laptop-2018",
  "type": "PARTITION",
  "rootPath": "/dev/sdb1",
  "physicalId": {
    "diskUuid": "abc-123-def",
    "partitionUuid": "def-456-ghi",
    "volumeLabel": "BACKUP_2018",
    "serialNumber": "WD-WCC4E123456",
    "mountPoint": "/media/haris/BACKUP_2018",
    "filesystemType": "ext4",
    "capacity": 4000000000000,
    "usedSpace": 2500000000000,
    "physicalLabel": "WD #17",
    "notes": "Dad's old laptop, full backup"
  },
  "parentSourceId": null,
  "childSourceIds": [],
  "status": "COMPLETED",
  "postponed": false,
  "totalFiles": 15432,
  "totalSize": 2500000000000,
  "processedFiles": 15432,
  "processedSize": 2500000000000,
  "scanStartedAt": "2025-11-28T10:00:00Z",
  "scanCompletedAt": "2025-11-28T12:30:00Z",
  "createdAt": "2025-11-28T10:00:00Z"
}
```

### File Batch JSON (`batch-0001.json`)

```json
{
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "batchNumber": 1,
  "files": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "sourceId": "550e8400-e29b-41d4-a716-446655440000",
      "path": "/photos/2018/IMG_1234.jpg",
      "name": "IMG_1234.jpg",
      "extension": "jpg",
      "size": 2457600,
      "sha256": "a1b2c3d4e5f6...",
      "modifiedAt": "2018-06-15T14:30:00Z",
      "createdAt": "2018-06-15T14:30:00Z",
      "accessedAt": "2025-11-28T10:15:00Z",
      "mimeType": "image/jpeg",
      "exif": {
        "cameraMake": "Apple",
        "cameraModel": "iPhone 8",
        "dateTimeOriginal": "2018-06-15T14:30:00Z",
        "width": 4032,
        "height": 3024,
        "orientation": 1,
        "gps": {
          "latitude": 37.7749,
          "longitude": -122.4194,
          "altitude": 15.0
        },
        "lensModel": "iPhone 8 back camera 3.99mm f/1.8",
        "focalLength": 3.99,
        "aperture": 1.8,
        "shutterSpeed": "1/120",
        "iso": 20,
        "flash": false
      },
      "status": "HASHED",
      "isDuplicate": false,
      "scannedAt": "2025-11-28T10:15:23Z"
    }
    // ... 999 more files
  ]
}
```

### Summary JSON (`summary.json`)

```json
{
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "totalFiles": 15432,
  "totalSize": 2500000000000,
  "totalBatches": 16,
  "postponedArchives": 3,
  "skippedFiles": 2,
  "errors": [
    {
      "file": "/system/protected.dat",
      "error": "Permission denied"
    },
    {
      "file": "/corrupted.jpg",
      "error": "Failed to extract EXIF"
    }
  ],
  "duration": 9000000,  // milliseconds
  "startTime": "2025-11-28T10:00:00Z",
  "endTime": "2025-11-28T12:30:00Z",
  "scannerVersion": "0.1.0",
  "scannerHost": "haris-laptop",
  "scannerUser": "haris"
}
```

## Hash Computation

### SHA-256 Implementation

```java
public String computeHash(Path file) throws IOException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");

    try (InputStream is = Files.newInputStream(file)) {
        byte[] buffer = new byte[8192];  // 8 KB buffer
        int read;

        while ((read = is.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
    }

    byte[] hashBytes = digest.digest();
    return bytesToHex(hashBytes);
}

private static String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) {
        result.append(String.format("%02x", b));
    }
    return result.toString();
}
```

### Parallel Hashing

```java
ExecutorService hashExecutor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);

List<CompletableFuture<FileDto>> futures = files.stream()
    .map(file -> CompletableFuture.supplyAsync(
        () -> processFile(file),
        hashExecutor
    ))
    .collect(Collectors.toList());

List<FileDto> results = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
```

### Performance Targets

- **SSD:** 500+ MB/sec
- **HDD:** 100+ MB/sec
- **Memory usage:** < 500 MB
- **CPU usage:** 80-90% during hashing

## EXIF Extraction

### Metadata Extractor Integration

```java
public ExifMetadata extractExif(Path file) throws IOException {
    try {
        Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

        ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

        return ExifMetadata.builder()
            .cameraMake(getCameraMake(metadata))
            .cameraModel(getCameraModel(metadata))
            .dateTimeOriginal(getDateTimeOriginal(exifDir))
            .width(getWidth(exifDir))
            .height(getHeight(exifDir))
            .orientation(getOrientation(exifDir))
            .gps(extractGps(gpsDir))
            .lensModel(getLensModel(exifDir))
            .focalLength(getFocalLength(exifDir))
            .aperture(getAperture(exifDir))
            .shutterSpeed(getShutterSpeed(exifDir))
            .iso(getIso(exifDir))
            .flash(getFlash(exifDir))
            .build();
    } catch (Exception e) {
        // Log warning, return null
        log.warn("Failed to extract EXIF from {}: {}", file, e.getMessage());
        return null;
    }
}
```

### Duplicate Hash Optimization

```java
// Before extracting EXIF:
public FileDto processFile(Path file) {
    String hash = hashService.computeHash(file);

    // Check if hash exists in cache
    boolean hashExists = hashCache.contains(hash);

    ExifMetadata exif = null;
    if (!hashExists && isImageFile(file)) {
        // First time seeing this hash, extract EXIF
        exif = exifExtractor.extractExif(file);
        hashCache.add(hash);
    }
    // If hash exists, skip EXIF extraction

    return FileDto.builder()
        .hash(hash)
        .exif(exif)  // null if skipped
        // ... other fields
        .build();
}
```

## Error Handling

### Error Categories

**File-level errors** (Continue scanning):
- Permission denied
- File not found (deleted during scan)
- Corrupt file (can't read)
- EXIF extraction failed

**Network errors** (Retry with backoff):
- Connection timeout
- Server unreachable
- API error (500, etc.)

**Fatal errors** (Abort scan):
- Out of memory
- Disk full (output dir)
- Invalid configuration

### Error Handling Strategy

```java
public Optional<FileDto> processFile(Path file) {
    try {
        return Optional.of(doProcessFile(file));
    } catch (AccessDeniedException e) {
        log.warn("Permission denied: {}", file);
        errors.add(new FileError(file, "Permission denied"));
        return Optional.empty();  // Skip file
    } catch (IOException e) {
        log.error("Failed to process: {}", file, e);
        errors.add(new FileError(file, e.getMessage()));
        return Optional.empty();  // Skip file
    } catch (OutOfMemoryError e) {
        log.fatal("Out of memory!");
        throw new ScannerException("Out of memory", e);  // Fatal
    }
}
```

### Exit Codes

- **0** - Success (no errors)
- **1** - Partial success (some files skipped)
- **2** - Fatal error (scan aborted)
- **3** - Configuration error
- **4** - Invalid arguments

## Testing

### Unit Tests

**HashService:**
```java
@Test
void testHashComputation() {
    Path testFile = createTestFile("hello world");
    String hash = hashService.computeHash(testFile);
    assertEquals(
        "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
        hash
    );
}
```

**ExifExtractor:**
```java
@Test
void testExifExtraction() {
    Path testImage = loadTestImage("sample.jpg");
    ExifMetadata exif = exifExtractor.extractExif(testImage);

    assertEquals("Apple", exif.getCameraMake());
    assertEquals("iPhone 13 Pro", exif.getCameraModel());
    assertNotNull(exif.getDateTimeOriginal());
    assertNotNull(exif.getGps());
}
```

**FileWalkerService:**
```java
@Test
void testFileWalking() {
    Path testDir = createTestDirectory(100);  // 100 files

    List<Path> files = walkerService.walk(testDir);

    assertEquals(100, files.size());
    assertTrue(files.stream().allMatch(Files::isRegularFile));
}
```

### Integration Tests

**End-to-end scan:**
```java
@Test
void testFullScan() {
    // Create test directory
    Path testDir = createTestDirectory();
    addTestFiles(testDir, 1000);
    addTestImages(testDir, 100);

    // Run scan
    ScanRequest request = ScanRequest.builder()
        .rootPath(testDir)
        .name("Test Scan")
        .dryRun(true)
        .build();

    ScanResult result = scannerService.executeScan(request);

    // Verify results
    assertEquals(1100, result.getTotalFiles());
    assertTrue(Files.exists(outputDir.resolve("source.json")));
    assertTrue(Files.exists(outputDir.resolve("files/batch-0001.json")));
    assertTrue(Files.exists(outputDir.resolve("summary.json")));
}
```

### Manual Testing

**Test Plan:**

1. **Basic scan** - 100 files, verify JSON output
2. **Large scan** - 10,000 files, measure performance
3. **EXIF extraction** - Scan directory with 1,000 photos
4. **Archive postponement** - Scan with archives, postpone some
5. **Copy to staging** - Scan with --copy-to, verify copies
6. **Error handling** - Scan with permission errors
7. **Progress reporting** - Verify console output
8. **Resumability** - Interrupt scan, resume

## Performance Optimization

### Memory Management

```java
// Stream files, don't load into memory
try (Stream<Path> paths = Files.walk(root)) {
    paths.filter(Files::isRegularFile)
         .forEach(this::processFile);
}

// Batch processing to control memory
List<FileDto> batch = new ArrayList<>(batchSize);
for (Path file : files) {
    batch.add(processFile(file));
    if (batch.size() >= batchSize) {
        writeBatch(batch);
        batch.clear();  // Release memory
    }
}
```

### Threading Strategy

- **File walking:** Single thread (I/O bound)
- **Hashing:** N threads (CPU bound, N = core count)
- **EXIF extraction:** Part of hash thread (I/O bound)
- **Writing batches:** Single thread (I/O bound)
- **Copy to staging:** M threads (I/O bound, M = 4)

### Buffer Sizes

- **Hash computation:** 8 KB
- **File copying:** 64 KB
- **JSON writing:** Buffered writer

## Future Enhancements

### Server Integration (Post-MVP)

Replace `JsonOutput` with `ApiClientService`:

```java
public interface OutputService {
    void createSource(SourceDto source);
    Map<String, Boolean> checkHashes(List<String> hashes);
    void sendFileBatch(FileBatchDto batch);
    void completeScan(CompleteScanRequest request);
}

// MVP implementation
public class JsonOutput implements OutputService { ... }

// Future implementation
public class ApiClientService implements OutputService {
    private final OkHttpClient httpClient;
    private final String serverUrl;
    private final String apiKey;

    @Override
    public void sendFileBatch(FileBatchDto batch) {
        Request request = new Request.Builder()
            .url(serverUrl + "/api/scan/files")
            .header("X-API-Key", apiKey)
            .post(RequestBody.create(
                objectMapper.writeValueAsString(batch),
                MediaType.parse("application/json")
            ))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
        }
    }
}
```

### Resume Functionality

Store scan state periodically:

```json
{
  "sourceId": "550e8400-e29b-41d4-a716-446655440000",
  "lastProcessedPath": "/photos/2018/IMG_1234.jpg",
  "processedFiles": 1234,
  "processedSize": 1234567890,
  "currentBatch": 2,
  "timestamp": "2025-11-28T10:15:23Z"
}
```

On resume:
1. Load state file
2. Verify source ID matches
3. Skip files up to `lastProcessedPath`
4. Continue scanning

### Incremental Scanning

Track file modification times:

```
If file.modifiedAt <= source.lastScanAt:
    Skip file (unchanged since last scan)
Else:
    Process file (new or modified)
```
