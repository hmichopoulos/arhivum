# Scanner Requirements

## Overview

The **archivum-scanner** is a lightweight CLI tool that runs on the user's computer where physical disks are connected. It walks file systems, computes hashes, extracts metadata, and sends results to the archivum-server via REST API.

## Goals

1. **Minimal footprint** - Small, fast, no database required
2. **Parallel execution** - Multiple scanners can run simultaneously on different computers
3. **Resumable** - Can continue from where it left off if interrupted
4. **Intelligent** - Detects physical device IDs, archives, and prompts for user input
5. **Flexible** - Supports dry-run mode without server communication

## Functional Requirements

### FR-1: Physical Device Identification

**Priority:** P0 (Must Have)

The scanner must detect and record physical identifiers for storage devices:

- **Disk UUID** (via `blkid` or equivalent)
- **Partition UUID**
- **Volume Label** (filesystem label)
- **Serial Number** (device serial)
- **Mount Point** (at scan time)
- **Filesystem Type** (ext4, ntfs, exfat, etc.)
- **Capacity** (total and used space)
- **Physical Label** (user-provided sticker label)
- **User Notes** (free-form text)

The scanner should:
- Auto-detect all available identifiers
- Prompt user for physical label and notes
- Provide smart defaults based on detected information
- Handle cases where some identifiers are unavailable

### FR-2: Source Hierarchy

**Priority:** P0 (Must Have)

The scanner must support hierarchical source relationships:

```
Disk "WD-4TB-Black-Sticker"
  └─ Partition "Main-Backup"
       ├─ Archive "acronis-2018.tib" (postponed)
       ├─ Archive "photos-2017.tar.gz" (postponed)
       └─ Normal files...
```

**Source Types:**
- `DISK` - Physical disk
- `PARTITION` - Partition on a disk
- `LVM_VOLUME` - LVM logical volume
- `ARCHIVE_TAR` - TAR/TAR.GZ/TAR.BZ2 archive
- `ARCHIVE_ZIP` - ZIP archive
- `ARCHIVE_7Z` - 7-Zip archive
- `ARCHIVE_IMG` - Disk image (ISO, IMG)
- `ARCHIVE_ACRONIS` - Acronis backup file
- `ARCHIVE_OTHER` - Other archive format

**Relationships:**
- Each source can have a parent source
- Parent-child relationships are tracked
- Archives can be postponed (cataloged but not scanned)

### FR-3: Archive Detection and Postponement

**Priority:** P0 (Must Have)

The scanner must detect archive files and allow postponement:

**Detection:**
- Identify archives by extension (.tar, .gz, .zip, .7z, .tib, .iso, .img, etc.)
- Detect large archive files (e.g., >1GB) that might be backup images

**Postponement:**
- Prompt user: "Found archive X (size Y). Scan contents now? [Y/n/postpone]"
- If postponed:
  - Record archive as a source with `postponed=true`
  - Compute hash of archive file itself
  - Record metadata (name, size, path, parent source)
  - Skip scanning contents
- User can later open postponed archives with `open-archive` command

**Benefits:**
- Avoid duplicate work across multiple disks
- User controls what to scan and when
- Detect duplicate archives by hash

### FR-4: File Walking and Hashing

**Priority:** P0 (Must Have)

The scanner must efficiently walk directories and compute hashes:

**Walking:**
- Traverse directory tree recursively
- Skip system directories (`.Trash`, `$RECYCLE.BIN`, etc.)
- Follow symlinks (optional, configurable)
- Handle permission errors gracefully
- Report progress (files/sec, bytes/sec)

**Hashing:**
- Compute SHA-256 for every file
- Stream files (don't load into memory)
- Use multiple threads for parallel hashing
- Handle large files efficiently (>1GB)
- Report progress per file

**Batching:**
- Batch results every N files (e.g., 1000)
- Send batches to server via REST API
- Continue on network errors (retry with backoff)

### FR-5: Metadata Extraction

**Priority:** P0 (Must Have)

The scanner must extract file metadata:

**Basic Metadata:**
- File name
- File extension
- File size
- Modification time
- Creation time (if available)
- Access time (if available)
- MIME type (detected)

**EXIF Metadata (for images):**
- Camera make and model
- Date/time original
- Image dimensions (width, height)
- Orientation
- GPS coordinates (lat, lon, altitude)
- Lens model
- Focal length
- Aperture, shutter speed, ISO
- Flash

**Optimization:**
- Check hash with server before extracting EXIF
- Skip EXIF extraction if hash already exists
- This allows multiple scanners to avoid redundant work

### FR-6: Duplicate Optimization

**Priority:** P0 (Must Have)

When multiple scanners run in parallel, avoid redundant metadata extraction:

1. **Before extracting metadata**, batch hashes and send `CheckHashRequest`
2. **Server responds** with map of hash → exists (true/false)
3. **Scanner skips EXIF** extraction for files with existing hashes
4. **Scanner still records** the file location (source, path) for provenance

This is critical when scanning 20+ disks with many duplicate photos.

### FR-7: Interactive Prompts

**Priority:** P0 (Must Have)

The scanner must prompt users for input when needed:

**When to prompt:**
- At scan start: source name, physical label, notes
- During scan: archive postponement decisions
- On errors: retry, skip, or abort

**Smart defaults:**
- Suggest name based on volume label or mount point
- Remember previous inputs for similar devices
- Allow non-interactive mode with config file

### FR-8: Dry-Run Mode

**Priority:** P0 (Must Have)

The scanner must support offline operation without server:

**Behavior:**
- Scan filesystem normally
- Compute hashes and extract metadata
- Write results to local JSON files
- Do NOT communicate with server
- Later import JSON files into server

**Use Cases:**
- Initial testing and debugging
- Scanning disks offline
- Reviewing results before importing
- Backup of scan results

**File Format:**
```json
{
  "source": { /* SourceDto */ },
  "files": [ /* FileDto[] */ ],
  "metadata": {
    "scannerVersion": "0.1.0",
    "scannedAt": "2025-11-28T10:00:00Z",
    "totalFiles": 12345,
    "totalSize": 1234567890
  }
}
```

### FR-9: Copy to Staging

**Priority:** P1 (Should Have)

The scanner can optionally copy files to NAS staging area:

**Behavior:**
- After computing hash, copy file to staging
- Verify hash after copy
- Report copy progress
- Handle copy errors (disk full, permission denied)
- Resume interrupted copies

**Configuration:**
```yaml
copy:
  enabled: true
  target: /mnt/nas/Archivum/Staging/{source-id}/
  verify: true
  resume: true
```

### FR-10: Progress Reporting

**Priority:** P0 (Must Have)

The scanner must provide clear progress feedback:

**Console output:**
```
Scanning: /media/disk1
Source: Backup-2018 (PARTITION)
Files: 1,234 / 12,345 (10%)
Size: 1.2 GB / 12.3 GB (10%)
Speed: 120 files/sec, 45 MB/sec
Hashing: IMG_1234.jpg (2.4 MB)
```

**Progress updates:**
- Files scanned
- Bytes processed
- Current file
- Files/sec and bytes/sec
- Estimated time remaining

### FR-11: Resumability

**Priority:** P1 (Should Have)

The scanner can resume interrupted scans:

**Mechanism:**
- Store scan state in local file (`.archivum-scan-state.json`)
- Record last processed path
- On resume, skip already-processed files
- Verify state is still valid (source ID matches)

**State file:**
```json
{
  "sourceId": "uuid",
  "lastPath": "/photos/2018/IMG_1234.jpg",
  "processedFiles": 1234,
  "processedSize": 1234567890,
  "timestamp": "2025-11-28T10:00:00Z"
}
```

### FR-12: Configuration

**Priority:** P1 (Should Have)

The scanner supports configuration via file and CLI:

**Config file** (`~/.config/archivum/scanner.yml`):
```yaml
server:
  url: https://archivum.local:8080
  apiKey: ${ARCHIVUM_API_KEY}

scanner:
  threads: 8
  batchSize: 1000
  skipSystemDirs: true
  followSymlinks: false

copy:
  enabled: false
  target: /mnt/nas/Archivum/Staging/
  verify: true

dryRun:
  enabled: false
  outputDir: ~/.local/share/archivum/scans/
```

**CLI overrides:**
```bash
archivum scan /media/disk1 \
  --name "Backup-2018" \
  --threads 16 \
  --dry-run \
  --copy-to /mnt/nas/Staging/
```

## Non-Functional Requirements

### NFR-1: Performance

- **Hashing speed:** At least 100 MB/sec on SSD
- **Parallel hashing:** Use all available CPU cores
- **Memory usage:** Max 500 MB for scanner process
- **Batch size:** Send files in batches of 1000

### NFR-2: Reliability

- **Error handling:** Graceful degradation on errors
- **Retry logic:** Exponential backoff for network errors
- **State persistence:** Scan state saved every 1000 files
- **Data integrity:** Hash verification after copy

### NFR-3: Usability

- **Clear output:** Progress bars and status messages
- **Smart defaults:** Minimal user input required
- **Help text:** Comprehensive `--help` output
- **Error messages:** Actionable error messages

### NFR-4: Portability

- **Java 21:** Runs on any platform with Java 21+
- **Single JAR:** Fat JAR with all dependencies
- **No installation:** Just download and run
- **Cross-platform:** Linux, macOS, Windows

## Success Criteria

The scanner is considered successful if:

1. ✅ Can scan a 4TB disk with 1M files in < 8 hours
2. ✅ Uses < 500 MB memory during scan
3. ✅ Successfully detects physical device IDs
4. ✅ Correctly identifies and postpones archives
5. ✅ Extracts EXIF from 10,000 photos in < 30 minutes
6. ✅ Handles network errors gracefully (retry, resume)
7. ✅ Works in dry-run mode without server
8. ✅ Multiple scanners can run in parallel without conflicts
9. ✅ Resumable after interruption with < 1% duplicate work
10. ✅ User-friendly prompts and progress reporting

## Out of Scope

The following are explicitly out of scope for the scanner:

- ❌ **Deduplication logic** - handled by server
- ❌ **Classification** - handled by server
- ❌ **File migration** - handled by server
- ❌ **Database** - scanner is stateless
- ❌ **GUI** - CLI only (UI is separate React app)
- ❌ **File deletion** - scanner never deletes files
- ❌ **Archive extraction** - archives are cataloged, not extracted (postponed)
- ❌ **Cloud scanning** - MVP is local disks only

## Future Enhancements

Potential future improvements (not in MVP):

- 🔮 Cloud source scanning (Google Drive, OneDrive, etc.)
- 🔮 SSH/SFTP remote scanning
- 🔮 Incremental scans (only scan new/modified files)
- 🔮 Parallel archive scanning (extract and scan postponed archives)
- 🔮 Advanced filters (exclude patterns, min/max size)
- 🔮 Audio/video metadata extraction
- 🔮 Document metadata extraction (PDF, Office files)
- 🔮 Machine learning for photo classification
- 🔮 GUI wrapper for scanner (optional)
