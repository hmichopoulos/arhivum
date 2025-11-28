# Deduplication Logic

## Overview

Archivum performs deduplication at two levels:
1. **File-level:** Find files with identical content (same SHA-256 hash)
2. **Folder-level:** Find folders with identical or similar content

Different zones have different deduplication rules to avoid breaking software installations.

## Zones and Dedup Rules

| Zone | File Dedup | Folder Dedup | Rationale |
|------|------------|--------------|-----------|
| MEDIA | ✅ Yes | ✅ Yes | Same photo is same photo |
| DOCUMENTS | ✅ Yes | ✅ Yes | Same document is same document |
| BOOKS | ✅ Yes | ✅ Yes | Same ebook is same ebook |
| SOFTWARE | ❌ No | ✅ Yes | DLLs are shared, don't break installs |
| BACKUP | ❌ No | ✅ Yes | Preserve backup integrity |
| CODE | ❌ No | ✅ Yes | Repos have shared files |
| UNKNOWN | ❌ No | ❌ No | Don't touch until classified |

## File-Level Deduplication

### Algorithm

```
1. Group files by SHA-256 hash
2. For each group with count > 1:
   a. Check zone of all files in group
   b. If ANY file is in non-dedup zone (SOFTWARE, BACKUP, CODE):
      → Skip this group (or handle specially)
   c. Otherwise:
      → Create DuplicateGroup
      → Recommend which copy to keep
```

### Choosing Which Copy to Keep

Priority (highest first):
1. File already in `/Archive` (previously organized)
2. File in most recently scanned source
3. File with earliest modification date (original)
4. File with shortest path (less nested = likely more intentional)

```java
public ScannedFile selectBestCopy(List<ScannedFile> duplicates) {
    return duplicates.stream()
        .min(Comparator
            .comparing((ScannedFile f) -> f.getArchivePath() == null) // Archive first
            .thenComparing(f -> f.getSource().getScannedAt(), Comparator.reverseOrder())
            .thenComparing(ScannedFile::getModifiedAtSource)
            .thenComparing(f -> f.getOriginalPath().length()))
        .orElseThrow();
}
```

### Mixed-Zone Duplicates

When duplicates span multiple zones:

```
Scenario:
  - File A: /Photos/vacation.jpg (MEDIA zone)
  - File B: /Software/PhotoApp/samples/vacation.jpg (SOFTWARE zone)
  
Same hash, but:
  - File A can be deduplicated
  - File B must stay (part of software)

Resolution:
  - Mark File A as duplicate
  - Keep File B (even though it's in SOFTWARE)
  - OR: Keep File A (in organized location), keep File B (in software)
  
The key: Never remove a file that's part of SOFTWARE zone.
```

## Folder-Level Deduplication

### Why Folder-Level?

File-level dedup finds individual duplicate files. But you might have:

```
/Disk1/Backup_2018/Photos/
  - 10,000 files

/Disk3/Backup_2019/Photos/
  - 10,500 files (includes all of 2018 + 500 new)

File-level would find 10,000 duplicate pairs.
Folder-level recognizes: "These folders are 95% identical"
```

### Content Hash

Each folder gets a "content hash" computed from its files:

```java
public String computeFolderContentHash(FolderSummary folder) {
    List<String> fileHashes = getFileHashesInFolder(folder)
        .stream()
        .sorted()
        .collect(toList());
    
    return sha256(String.join(",", fileHashes));
}
```

Two folders with identical content hash are 100% identical.

### Similarity Calculation

For folders that aren't 100% identical:

```java
public double computeFolderSimilarity(FolderSummary a, FolderSummary b) {
    Set<String> hashesA = getFileHashesInFolder(a);
    Set<String> hashesB = getFileHashesInFolder(b);
    
    Set<String> intersection = new HashSet<>(hashesA);
    intersection.retainAll(hashesB);
    
    Set<String> union = new HashSet<>(hashesA);
    union.addAll(hashesB);
    
    // Jaccard similarity
    return (double) intersection.size() / union.size() * 100;
}
```

### Folder Duplicate Groups

```
FolderDuplicateGroup:
  - similarity: 94.5%
  - members:
    - /Disk1/Backup_2018/Photos/ (561 unique files, 1.4 GB)
    - /Disk3/Backup_2019/Photos/ (2,329 unique files, 8.3 GB)
    - Common: 9,892 files (43.8 GB)
```

### Merge Strategies

When folders are similar:

| Strategy | Description |
|----------|-------------|
| KEEP_ALL | Keep both folders as-is |
| MERGE_KEEP_UNIQUE | Merge into one, keeping all unique files from both |
| KEEP_LARGER | Keep the folder with more files, discard other |
| KEEP_NEWER | Keep the more recently modified folder |

For backups, `MERGE_KEEP_UNIQUE` is usually best: you get everything from all backups in one place.

## Software Root Detection

### The Problem

Software folders contain shared DLLs that look like duplicates:

```
/Software/PhotoshopCS6/Support/vcruntime140.dll
/Software/Illustrator/Support/vcruntime140.dll
/Software/Premiere/Support/vcruntime140.dll

Same file, but each application needs its copy.
```

### Root Markers

Identify where a "software unit" starts:

| Marker | Type |
|--------|------|
| `setup.exe`, `install.exe`, `uninstall.exe` | Windows installer |
| `*.msi` | Windows installer package |
| `*.app` | macOS application bundle |
| `*.dmg` | macOS disk image |
| Executable + DLLs in same folder | Windows application |
| `package.json` + `node_modules/` | Node.js project |
| `pom.xml` | Maven project |
| `build.gradle` | Gradle project |
| `.git/` | Git repository |

### Detection Algorithm

```java
public Optional<Path> findSoftwareRoot(Path file) {
    Path current = file.getParent();
    
    while (current != null && isInSoftwareZone(current)) {
        if (hasRootMarker(current)) {
            return Optional.of(current);
        }
        current = current.getParent();
    }
    
    return Optional.empty(); // Flag for manual review
}

private boolean hasRootMarker(Path folder) {
    // Check for installer executables
    if (containsFile(folder, "setup.exe", "install.exe", "uninstall.exe")) {
        return true;
    }
    
    // Check for MSI
    if (hasFileWithExtension(folder, "msi")) {
        return true;
    }
    
    // Check for Mac app bundle
    if (folder.toString().endsWith(".app")) {
        return true;
    }
    
    // Check for executable + DLLs (application root)
    if (hasFileWithExtension(folder, "exe") && hasFileWithExtension(folder, "dll")) {
        return true;
    }
    
    // Check for project roots
    if (containsFile(folder, "package.json", "pom.xml", "build.gradle")) {
        return true;
    }
    
    // Check for git repo
    if (containsFolder(folder, ".git")) {
        return true;
    }
    
    return false;
}
```

### Depth Resolution

What if there are nested markers?

```
/Software/GamePlatform/
├── Steam.exe                 ← ROOT marker (Steam itself)
├── steamapps/
│   └── common/
│       └── CoolGame/
│           └── game.exe      ← Another ROOT marker
```

**Rule: First root wins.** Everything under `/GamePlatform/` is one unit.

Exception: Known platforms (Steam, GOG, Epic) can have special handling to recognize individual games.

### Software Folder Dedup

Once roots are identified, folder-level dedup works:

```
/Disk1/Software/PhotoshopCS6/  (hash of all contents: abc123)
/Disk5/Installers/PhotoshopCS6/ (hash of all contents: abc123)

→ "These are the same installer. Keep one, delete the other FOLDER."
```

## UI for Manual Override

### Zone Override

User can change zone of any folder:

```
/Disk1/RandomFolder/
  Detected zone: UNKNOWN
  [MEDIA ▼] [DOCUMENTS] [SOFTWARE] [BACKUP] [CODE]
  
  Apply to: ○ This folder only
            ● This folder and subfolders
```

### Software Root Override

User can adjust detected roots:

```
/Disk1/Software/Adobe/
  
  Detected roots:
  ✓ /Adobe/PhotoshopCS6/  [Marker: Setup.exe]
  ✓ /Adobe/Illustrator/   [Marker: Setup.exe]
  ⚠ /Adobe/SharedTools/    [No marker found]
  
  Actions:
  [Mark as Root] [Mark as NOT Root] [Merge with Parent]
```

### Duplicate Review

User reviews each duplicate group:

```
GROUP: vacation_photo.jpg (4.2 MB) — 3 copies

○ /Disk1/Photos/2015/vacation_photo.jpg        2015-06-12
● /Disk2/Backup/Photos/vacation_photo.jpg      2015-06-12  [KEEP]
○ /Disk3/Old/vacation_photo.jpg                2015-06-12

[Keep Selected] [Keep Oldest] [Keep All] [Skip]
```

## Safety Mechanisms

### No Auto-Delete

Archivum never automatically deletes files. The workflow is:

1. Scan and analyze
2. Mark duplicates in database
3. User reviews and approves
4. Only then: staged for deletion (moved to trash first)
5. After verification: permanent delete

### Provenance Tracking

Every file tracks where it came from:

```sql
-- Original location preserved
original_path: "/Disk1/Photos/2015/vacation.jpg"
source_id: "Disk1-Backup-2018"

-- Even after migration
archive_path: "/Archive/Life/Daily/2015/2015-06/vacation.jpg"
-- Original info still available for reference
```

### Dry Run

Before any destructive operation:

```
DRY RUN: Duplicate Resolution

Would keep:
  - /Disk2/Backup/Photos/vacation_photo.jpg

Would mark as duplicate:
  - /Disk1/Photos/2015/vacation_photo.jpg
  - /Disk3/Old/vacation_photo.jpg

Space to be recovered: 8.4 MB

[Proceed] [Cancel]
```

## Performance Considerations

### Hashing

- SHA-256 is cryptographically secure but slower
- For initial size-based grouping, just compare file sizes
- Only hash files that have size matches
- Use BufferedInputStream for efficient reading
- Parallelize hashing across multiple threads

### Folder Similarity

- Store content hash for each folder
- Only compute similarity for folders with same/similar total size
- Use approximate matching for initial filtering

### Large Collections

- Process in batches (1000 files at a time)
- Update UI via WebSocket without blocking
- Allow pause/resume of analysis
