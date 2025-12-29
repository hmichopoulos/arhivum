# Migration Workflow & Requirements

**Status**: Design Phase - No Implementation Yet
**Last Updated**: 2025-12-29

---

## Table of Contents

- [Overview](#overview)
- [Key Requirements](#key-requirements)
- [Migration Strategies](#migration-strategies)
- [Destinations](#destinations)
- [User Workflow](#user-workflow)
- [Realistic Scenarios](#realistic-scenarios)
- [UI/UX Design](#uiux-design)
- [Open Questions](#open-questions)

---

## Overview

Users have different needs for different types of files:
- **Code projects** → Git repositories (manual setup)
- **Documents** → Private NAS space (automatic, organized)
- **Software/Books** → Shared NAS folders (automatic)
- **Archives/Backups** → Keep on external disks (warehouse)
- **Duplicates** → Manual review and decisions

**Core principle**: System suggests, user decides, system executes (with varying levels of automation).

---

## Key Requirements

### 0. Already-Organized Files (Baseline Scan)

**Scenario**: User has already organized some files on NAS or other locations. System needs to:
1. Avoid re-migrating these files
2. Detect duplicates against already-organized files
3. Preserve existing organization

**Solution**: **Destination Scanning with Pinning**

**Workflow**:

1. **One-time setup**: User scans already-organized locations
   ```bash
   ./archivum-scanner scan \
     --server-url http://server:8080 \
     --name "NAS Archive (Existing)" \
     --source-type DESTINATION \
     /mnt/nas/Archive/
   ```

2. **System actions**:
   - Computes hashes for all files
   - Marks source type as: `DESTINATION`
   - Marks all files as: `PINNED` (already in final location)
   - Stores paths as canonical destinations

3. **When scanning new disks**:
   - System computes hash: `abc123...`
   - Checks against PINNED files
   - If match found:
     - Shows: "✓ Already in archive at `/NAS/Archive/Private/Documents/2024/tax.pdf`"
     - Suggests: "Safe to delete from source disk"
     - Prevents re-migration

4. **Benefits**:
   - Avoids duplicate work (don't re-migrate what's already organized)
   - Enables cleanup (know what's safe to delete from disks)
   - Preserves existing organization
   - Still enables deduplication

**Source Types**:

| Type | Purpose | Files State | Migration Behavior |
|------|---------|-------------|-------------------|
| **DISCOVERY** | Find files to migrate | DISCOVERED | Plan migration |
| **DESTINATION** | Already organized | PINNED | Never migrate, use for dedup |
| **WAREHOUSE** | Catalog only | WAREHOUSED | Stay on disk, searchable |

**UI Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│  Scan Source                                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Source Type:                                                │
│                                                              │
│  ○ Discovery (default)                                       │
│    Scan external disk or cloud to find files to migrate     │
│                                                              │
│  ○ Destination                                               │
│    Scan already-organized location (e.g., NAS Archive)       │
│    Files will be PINNED (won't be migrated again)           │
│                                                              │
│  ○ Warehouse                                                 │
│    Catalog files that will stay on this disk                │
│                                                              │
│  Path: [/mnt/nas/Archive/                               ]   │
│  Name: [NAS Archive (Existing Organization)             ]   │
│                                                              │
│  [Cancel] [Start Scan]                                       │
└─────────────────────────────────────────────────────────────┘
```

**Example Scenario**:

**Setup**:
```bash
# Scan already-organized NAS folder
./archivum-scanner scan \
  --source-type DESTINATION \
  --name "NAS Archive" \
  /mnt/nas/Archive/

# Result: 2M files hashed and PINNED
# - /NAS/Archive/Private/haris/Documents/2024/taxes.pdf (hash: abc123)
# - /NAS/Archive/Private/haris/Photos/2024/vacation.jpg (hash: def456)
# - ...
```

**Discovery**:
```bash
# Scan external disk
./archivum-scanner scan \
  --source-type DISCOVERY \
  --name "WD Blue 4TB" \
  /mnt/disk1/

# System finds:
# - /mnt/disk1/old_stuff/taxes_2024.pdf (hash: abc123)
#   → Matches PINNED file: /NAS/Archive/.../taxes.pdf
#   → Show: "✓ Already in archive"
#   → Suggest: "Safe to delete from WD Blue"
#
# - /mnt/disk1/photos/IMG_1234.jpg (hash: def456)
#   → Matches PINNED file: /NAS/Archive/.../vacation.jpg
#   → Show: "✓ Already in archive"
#
# - /mnt/disk1/new_doc.pdf (hash: xyz789)
#   → No match
#   → Show: "New file, needs migration"
```

**UI View**:

```
┌─────────────────────────────────────────────────────────────┐
│  Files on: WD Blue 4TB                                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Status Filter: [All ▼] [Already in Archive] [New]          │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✓ old_stuff/taxes_2024.pdf                           │   │
│  │   Status: Already in Archive                         │   │
│  │   Location: /NAS/Archive/.../Documents/2024/taxes.pdf│   │
│  │   Action: [Mark for Deletion] [Keep on Disk]         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✓ photos/IMG_1234.jpg                                │   │
│  │   Status: Already in Archive                         │   │
│  │   Location: /NAS/Archive/.../Photos/2024/vacation.jpg│   │
│  │   Action: [Mark for Deletion] [Keep on Disk]         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │   new_doc.pdf                                         │   │
│  │   Status: New (needs migration)                       │   │
│  │   Suggested: /NAS/Archive/.../Documents/2025/        │   │
│  │   Action: [Plan Migration] [Ignore]                  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Summary:                                                    │
│  ├─ Already in Archive: 850 files (2.1 TB) ✓               │
│  ├─ New files: 120 files (450 GB) → Need migration         │
│  └─ Safe to delete: 850 files (if you trust archive)       │
│                                                              │
│  [Delete Already-Archived Files] [Plan Migration for New]   │
└─────────────────────────────────────────────────────────────┘
```

**Database Schema Addition**:

```sql
-- Source types
CREATE TYPE source_type AS ENUM (
  'DISCOVERY',    -- Files to be migrated
  'DESTINATION',  -- Already organized (pin files)
  'WAREHOUSE'     -- Catalog only (files stay on disk)
);

ALTER TABLE source ADD COLUMN source_type source_type DEFAULT 'DISCOVERY';

-- File states
CREATE TYPE file_state AS ENUM (
  'DISCOVERED',   -- Found during scan
  'PINNED',       -- Already in final location (don't migrate)
  'STAGED',       -- Ready to migrate
  'MIGRATED',     -- Successfully migrated
  'WAREHOUSED',   -- Cataloged, stays on disk
  'DELETED'       -- Marked for deletion
);

ALTER TABLE scanned_file ADD COLUMN state file_state DEFAULT 'DISCOVERED';

-- When source_type = DESTINATION, all files automatically get state = PINNED
```

**Migration Logic**:

```java
// When planning migration
for (File file : filesToMigrate) {
    // Check if file already exists in PINNED location
    Optional<ScannedFile> pinnedVersion = findByHashAndState(
        file.getContentHash(),
        FileState.PINNED
    );

    if (pinnedVersion.isPresent()) {
        // File already in archive
        file.setState(FileState.ALREADY_IN_ARCHIVE);
        file.setPinnedLocation(pinnedVersion.get().getPath());
        // Don't add to migration plan
    } else {
        // New file, needs migration
        addToMigrationPlan(file);
    }
}
```

---

### 1. Multiple Destinations

Not everything goes to one place:

| Zone | Typical Destination | Strategy |
|------|-------------------|----------|
| **CODE** | Git repositories | Manual (user creates repo, provides URL) |
| **DOCUMENTS** | Private NAS (`/NAS/Private/Documents/`) | Automatic with review |
| **BOOKS** | Shared NAS (`/NAS/Shared/Books/`) | Automatic |
| **SOFTWARE** | Shared NAS (`/NAS/Shared/Software/`) | Automatic |
| **MEDIA** | Private NAS (`/NAS/Private/Photos/`, `/Videos/`) | Automatic with organization |
| **BACKUP** | Warehouse (keep on disk) | Manual catalog only |
| **UNKNOWN** | Requires classification first | Manual review |

### 2. Different Migration Strategies

**Automatic Migration**:
- System copies files to destination
- No user intervention during copy
- User approves plan, system executes

**Semi-Manual Migration**:
- System prepares files
- User performs final step (e.g., git push)
- System tracks completion

**Manual Migration**:
- User decides what to do
- System provides information
- User marks as "handled"

**Warehouse (No Migration)**:
- Files stay on original disk
- System catalogs location
- Physical disk labeled and stored

### 3. Duplicate Handling Flexibility

**Exact Duplicates**:
- System suggests: "Keep 1, delete others"
- User can override: keep all, keep specific ones

**Similar Files (fuzzy duplicates)**:
- Code projects with different versions
- Photos with slight edits
- Documents with revisions
- **User reviews and decides**: keep, merge, delete, archive separately

### 4. Manual Intervention Points

Users need control at:
1. **Classification**: Override AI suggestions
2. **Deduplication**: Choose which duplicates to keep
3. **Destination**: Override default destinations
4. **Execution**: Review plan before migration
5. **Verification**: Confirm completion

---

## Migration Strategies

### Strategy 1: GIT_ARCHIVE (Code Projects)

**Use case**: Migrate code projects to Git repositories

**User workflow**:
1. System detects code project (e.g., "my-api" Maven project)
2. User reviews in "Code Projects" tab
3. User decides: "Archive to Git"
4. **Manual step**: User creates Git repo on their server (GitHub/GitLab/self-hosted)
5. User provides Git URL in UI
6. System creates migration task:
   - Copies project to temporary staging area
   - Initializes git repo (if not already)
   - Adds remote
   - Shows instructions: "Run: `cd /staging/my-api && git push`"
7. **Manual step**: User commits and pushes
8. User clicks "Mark as Complete" in UI
9. System updates database: project migrated
10. System optionally deletes from staging

**System responsibilities**:
- Detect code projects
- Group duplicates
- Prepare staging area
- Track status

**User responsibilities**:
- Create Git repo
- Provide URL
- Commit and push code
- Confirm completion

---

### Strategy 2: AUTO_COPY (Documents, Books, Software)

**Use case**: Automatic migration to NAS with organization

**User workflow**:
1. System scans and classifies files
2. System generates migration plan:
   - 5,000 documents → `/NAS/Private/Documents/[Year]/[Type]/`
   - 200 ebooks → `/NAS/Shared/Books/[Author]/[Title].epub`
   - 50 installers → `/NAS/Shared/Software/[Category]/[Name]/`
3. User reviews plan in UI (table showing source → destination)
4. User can:
   - Approve all
   - Exclude specific files
   - Change destinations for specific files
   - Change organization rules
5. User clicks "Execute Migration"
6. System:
   - Copies files (showing progress)
   - Verifies checksums
   - Updates database
   - Optionally deletes from source (user choice)
7. User reviews completion report

**System responsibilities**:
- Classify files
- Suggest folder structure
- Execute copy
- Verify integrity
- Track progress

**User responsibilities**:
- Review and approve plan
- Decide on deletion of source files

---

### Strategy 3: WAREHOUSE (Consolidate to External Disks)

**Use case**: Consolidate cold-storage files from multiple messy disks to fewer, organized, labeled warehouse disks

**Scenario**:
- User has 20 disks with files scattered everywhere
- System identifies 60TB of "cold" data (old archives, rarely accessed)
- User wants to consolidate 60TB → organized warehouse disks
- After deduplication: 60TB → 40TB (save space)
- Result: 10 clean warehouse disks (labeled, organized)
- Original 20 disks can be wiped and reused

**User workflow**:

1. **Scan all source disks** (DISCOVERY)
   - System catalogs all files
   - Identifies duplicates
   - Classifies hot vs cold data

2. **Decide split**:
   - 20TB "hot" data → Migrate to NAS
   - 60TB "cold" data → Migrate to Warehouse

3. **System generates warehouse plan**:
   - After dedup: 40TB needed
   - Suggests: 10 warehouse disks × 4TB
   - Shows organization:
     - WAREHOUSE-001: Old backups 2010-2012 (4TB)
     - WAREHOUSE-002: Old backups 2013-2015 (4TB)
     - WAREHOUSE-003: Software installers archive (4TB)
     - WAREHOUSE-004: Raw footage 2015-2018 (4TB)
     - ... etc

4. **User provides warehouse disks**:
   - Plugs in 10 empty disks (or disks to be wiped)
   - One at a time or multiple (if enough ports)

5. **System executes warehouse migration**:
   - Copies cold files to warehouse disks
   - Organizes by category or date
   - Deduplicates during copy
   - Generates labels for each disk
   - Catalogs all files in database

6. **User labels disks physically**:
   - Prints labels from system
   - Affixes to each warehouse disk
   - Stores in labeled location (Shelf A, Box 1-10)

7. **Original source disks**:
   - Wait 3 days for NAS backup
   - Format and wipe
   - Reuse or dispose

8. **Later retrieval**:
   - User searches: "project-backup-2012.zip"
   - System shows: "On WAREHOUSE-001, Shelf A, Box 1"
   - User retrieves disk, plugs in, gets file

**System responsibilities**:
- Plan warehouse consolidation
- Deduplicate during copy
- Copy files to warehouse disks
- Organize files logically
- Generate printable labels
- Catalog all files for search
- Track physical locations

**User responsibilities**:
- Decide what goes to warehouse (vs NAS)
- Provide warehouse disks
- Print and affix labels
- Store disks in labeled locations
- Update system if disks moved

---

### Strategy 4: MANUAL_REVIEW (Unknown, Complex)

**Use case**: Files that need manual decisions

**User workflow**:
1. System finds files it can't classify confidently
2. User reviews in "Pending Classification" tab
3. System provides:
   - File preview (if possible)
   - Suggested zone (with confidence %)
   - Similar files for context
4. User decides for each file:
   - Classify as [ZONE]
   - Mark for deletion
   - Assign to migration strategy
   - Defer decision
5. System updates classification
6. Files enter appropriate migration workflow

---

## Destinations

### NAS Structure

```
/volume1/Archive/
├── Private/
│   ├── haris/
│   │   ├── Documents/
│   │   │   ├── 2024/
│   │   │   │   ├── Financial/
│   │   │   │   ├── Medical/
│   │   │   │   └── Personal/
│   │   │   └── 2025/
│   │   ├── Photos/
│   │   │   ├── 2024/
│   │   │   │   ├── 01-January/
│   │   │   │   └── Family-Trip-Greece/
│   │   │   └── 2025/
│   │   └── Videos/
│   └── [other-family-members]/
│
├── Shared/
│   ├── Books/
│   │   ├── Technical/
│   │   │   ├── Programming/
│   │   │   └── DevOps/
│   │   ├── Fiction/
│   │   └── Non-Fiction/
│   ├── Software/
│   │   ├── Windows/
│   │   ├── macOS/
│   │   ├── Linux/
│   │   └── Mobile/
│   └── Family/
│       ├── Photos/
│       └── Videos/
│
└── Code/
    ├── GitHub/
    │   └── [repos are on GitHub, not NAS]
    ├── GitLab/
    │   └── [repos are on GitLab, not NAS]
    └── Local/
        └── [git repos stored on NAS]
```

### External Disks (Warehouse)

```
Physical Storage:
├── Shelf A/
│   ├── Box 1: WD-BLACK-001 (4TB) - Old backups 2010-2015
│   ├── Box 2: WD-BLUE-002 (4TB) - Software installers archive
│   └── Box 3: SEAGATE-003 (4TB) - Family videos raw footage
├── Shelf B/
│   └── Box 4: WD-RED-004 (4TB) - Music collection FLAC
└── Shelf C/
    └── Box 5: SAMSUNG-005 (2TB) - Work archives 2018-2020

Database tracks:
- Disk label
- Physical location
- File catalog
- Last verified date
```

---

## User Workflow

### Phase 1: Scanning & Discovery

**What user does**:
1. Plug in external HDD
2. Run scanner:
   ```bash
   ./archivum-scanner scan --server-url http://server:8080 --name "WD Blue 4TB" /mnt/disk
   ```
3. Wait for scan to complete (hours/days for large disks)
4. Repeat for all 20 disks

**System actions**:
- Compute hashes
- Detect code projects
- Store metadata
- NO file copying yet

**Timeline**: 1-2 weeks to scan all 80TB

---

### Phase 2: Analysis & Classification

**What user does**:
1. Access web UI: `http://server:3000`
2. Review "Dashboard" showing:
   - Total files: 16M
   - Total size: 80TB
   - Duplicates found: 4.5M files (30TB - can save!)
   - Needs classification: 500K files
3. Navigate to "Classification" tab
4. Review AI suggestions
5. Override/confirm classifications
6. Define custom rules (e.g., "All *.tax files → Documents/Financial")

**System actions**:
- AI classification (using Claude API)
- Duplicate detection
- Zone assignment
- Generate statistics

**Timeline**: Several days of user review sessions

---

### Phase 3: Duplicate Resolution

**What user does**:

**For exact duplicates**:
1. Navigate to "Duplicates" tab
2. See groups of identical files:
   - "vacation-photo.jpg" (5 copies across 3 disks)
   - System suggests: "Keep newest, delete 4 copies"
3. Review suggestion, approve or modify
4. Mark resolution: KEEP_NEWEST, KEEP_ALL, KEEP_SPECIFIC

**For code project duplicates**:
1. Navigate to "Code Projects" → "Duplicates" tab
2. See: "my-api" project found 3 times:
   - Version 1.0.0 (100 files) on "WD Blue"
   - Version 1.5.0 (105 files) on "Seagate"
   - Version 2.0.0 (110 files) on "WD Black"
3. System shows diff summary
4. User decides:
   - Keep v2.0.0 → Archive to Git
   - Keep v1.0.0 → Archive to Git (different branch)
   - Delete v1.5.0 → Mark for deletion

**System actions**:
- Group duplicates
- Suggest resolutions
- Show diffs for review
- Track decisions

**Timeline**: 1-2 weeks of review sessions

---

### Phase 4: Migration Planning

**What user does**:

1. Navigate to "Migration" tab
2. System shows migration plan overview:
   - **Auto-migrate**: 10M files (40TB) → Ready
   - **Git archive**: 150 projects → Needs manual setup
   - **Manual review**: 50K files → Needs decisions
   - **Warehouse**: 2M files (10TB) → 5 disks to label

3. **For auto-migrate files**:
   - Click "Documents" section
   - Review destination mappings (table view)
   - Approve or modify
   - Click "Add to migration queue"

4. **For Git projects**:
   - Navigate to "Code Projects" → "To Migrate"
   - For each project:
     - Click "Archive to Git"
     - Enter Git URL (e.g., `git@github.com:user/my-api.git`)
     - Add notes (e.g., "Production version, keep forever")
     - System adds to manual migration queue

5. **For warehouse disks**:
   - Select disk: "WD Blue 4TB"
   - Click "Warehouse this disk"
   - Enter label: "WD-BLUE-001"
   - Enter location: "Shelf A, Box 2"
   - Enter notes: "Old backups 2010-2015"
   - Click "Catalog as warehouse"

**System actions**:
- Generate migration plans
- Group by strategy
- Estimate time/space
- Create queue

**Timeline**: 2-3 days of planning

---

### Phase 5: Execution

**What user does**:

**For automatic migrations**:
1. Navigate to "Migration Queue"
2. Review queue (10M files)
3. Click "Start Migration"
4. Monitor progress:
   - Files copied: 1.2M / 10M
   - Progress: 12%
   - ETA: 3 days
   - Current: Copying "documents/2024/taxes.pdf"
5. System runs unattended
6. User checks periodically

**For Git projects**:
1. Navigate to "Manual Tasks"
2. See task: "Archive my-api to Git"
3. System shows:
   - Staging path: `/mnt/staging/my-api`
   - Git remote: `git@github.com:user/my-api.git`
   - Instructions:
     ```bash
     cd /mnt/staging/my-api
     git add .
     git commit -m "Archive from WD Black disk"
     git push origin main
     ```
4. User follows instructions
5. User clicks "Mark Complete"
6. System updates status

**For warehouse disks**:
1. System generates printable label:
   ```
   ╔═══════════════════════════════╗
   ║  WD-BLUE-001                  ║
   ║  4TB WD Blue Drive            ║
   ║  Old Backups 2010-2015        ║
   ║  Location: Shelf A, Box 2     ║
   ║  Files: 1.2M | Size: 3.8TB    ║
   ║  Cataloged: 2025-12-29        ║
   ╚═══════════════════════════════╝
   ```
2. User prints label
3. User affixes to disk
4. User stores disk in labeled location
5. User marks in system: "Stored"

**System actions**:
- Execute file copies
- Verify checksums
- Track progress
- Update database
- Generate labels
- Send notifications

**Timeline**: 1-2 weeks of migration (mostly automated)

---

### Phase 6: Verification & Cleanup

**What user does**:
1. Navigate to "Migration Status"
2. Review completed migrations:
   - Documents: 5M files ✓ Complete
   - Books: 200K files ✓ Complete
   - Code Projects: 120/150 ✓ In progress
   - Warehouse: 5 disks ✓ Complete
3. For completed items:
   - Verify files accessible on NAS
   - Spot-check random files
   - Confirm checksums match
4. For original disks:
   - Decide: Keep or wipe?
   - If keeping: Mark as "Backup copy"
   - If wiping: System shows what's safe to delete
5. Mark disks as "Ready to wipe" or "Keep as backup"

**System actions**:
- Generate verification reports
- Show checksums
- Track cleanup status
- Provide deletion scripts (with safety checks)

**Timeline**: 1-2 days of verification

---

## Realistic Scenarios

### Scenario 1: Developer with 20TB of Code Projects

**User**: Software developer with code scattered across multiple drives

**Files**:
- 200 code projects (Maven, Gradle, NPM)
- Many duplicates (same project, different versions)
- Mix of work projects, personal projects, experiments

**Workflow**:

1. **Scan** (Week 1):
   - Plug in 8 external HDDs
   - Run scanner on each
   - Scanner detects 200 projects, uploads metadata

2. **Review Duplicates** (Week 2):
   - System shows: "my-api" found 5 times (v1.0, v1.5, v2.0, v2.1, v2.1)
   - User reviews each:
     - v1.0: Delete (too old)
     - v1.5: Delete (abandoned)
     - v2.0: Archive to Git (stable release)
     - v2.1 (copy 1): Archive to Git (latest)
     - v2.1 (copy 2): Delete (exact duplicate)

3. **Setup Git Repos** (Week 3):
   - User creates repos on GitHub for 80 projects
   - For each project:
     - Create repo on GitHub
     - Enter URL in Archivum UI
     - System stages project
     - User pushes to Git
     - User marks complete

4. **Delete Old Projects** (Week 3):
   - 120 projects marked for deletion (old, abandoned, duplicates)
   - User reviews list one final time
   - User confirms deletion
   - System removes from staging

5. **Result**:
   - 80 projects archived to Git (organized, versioned)
   - 120 projects deleted (saved 15TB)
   - Original disks can be wiped or repurposed

---

### Scenario 2: Family with 40TB of Photos/Videos

**User**: Family with decades of photos/videos across many devices

**Files**:
- 500K photos (mix of RAW, JPEG, HEIC)
- 50K videos (mix of formats)
- Many duplicates (same photo on phone, laptop, cloud)
- Poor organization (random folder names)

**Workflow**:

1. **Scan** (Week 1-2):
   - Scan 10 external drives
   - Scan cloud accounts (OneDrive, Google Drive)
   - System finds 500K photos, 50K videos

2. **Deduplication** (Week 2):
   - System finds 150K exact duplicates (30%)
   - User reviews samples
   - User approves: "Keep newest copy, delete duplicates"
   - System creates dedup plan

3. **Classification** (Week 3):
   - System uses EXIF data to organize:
     - By date: `Photos/2024/01-January/`
     - By event (AI detects faces, locations): `Photos/2024/Family-Trip-Greece/`
   - User reviews suggested organization
   - User modifies some (e.g., renames events)
   - User approves migration plan

4. **Migration** (Week 3-4):
   - System copies 350K unique files to NAS
   - Organized structure: `/Archive/Private/haris/Photos/`
   - Progress: ~500GB per day
   - Total: 25TB → 18TB after dedup

5. **Verification** (Week 4):
   - User spot-checks random photos
   - Verifies albums intact
   - Confirms all accessible from NAS

6. **Cleanup** (Week 4):
   - User decides to keep one original disk as backup
   - Other 9 disks marked for wiping
   - System confirms: "All files verified on NAS, safe to wipe"

7. **Result**:
   - Photos organized by date and event
   - 30% duplicates removed (saved 7TB)
   - Accessible from NAS
   - One backup disk retained

---

### Scenario 3: Consultant with Work & Personal Mixed

**User**: IT consultant with client work, personal projects, documents

**Files**:
- 2TB work documents (client files, contracts, invoices)
- 5TB personal documents (taxes, medical, family)
- 500GB ebooks (technical books, fiction)
- 10TB software installers (collected over years)
- 3TB code projects (mix of client and personal)

**Workflow**:

1. **Scan** (Week 1):
   - Scan 5 drives
   - System catalogs 20TB total

2. **Classification** (Week 1-2):
   - System AI classifies documents:
     - "Invoice_ClientX.pdf" → Documents/Work/ClientX/
     - "Tax_2024.pdf" → Documents/Personal/Financial/
   - User reviews and corrects:
     - Some client files → Move to private (confidential)
     - Some personal → Move to shared (family)

3. **Destination Assignment** (Week 2):
   - Work documents → `/NAS/Private/haris/Work/`
   - Personal documents → `/NAS/Private/haris/Documents/`
   - Ebooks → `/NAS/Shared/Books/`
   - Software → `/NAS/Shared/Software/`
   - Code projects:
     - Client code → Archive to Git (private repos)
     - Personal code → Archive to Git (public repos)

4. **Migration** (Week 2-3):
   - Auto-migrate: Documents, ebooks, software (12TB)
   - Manual: Code projects (20 repos)
   - User sets up Git repos over several days
   - User pushes each project

5. **Result**:
   - Documents organized (work/personal separated)
   - Ebooks in shared library
   - Code in Git repositories
   - Software installers accessible (family can use)

---

### Scenario 4: Archivist with 80TB Warehouse

**User**: User with massive archive, wants to catalog without migrating everything

**Files**:
- 80TB across 20 disks
- Old backups (10 years worth)
- Historical data (may need someday)
- Mix of everything (code, docs, media, backups)

**Workflow**:

1. **Scan** (Week 1-4):
   - Scan all 20 disks
   - System catalogs 16M files
   - NO copying yet

2. **Analysis** (Week 4-5):
   - System identifies:
     - 5TB critical (recent work)
     - 20TB useful (organize and keep accessible)
     - 30TB archive (old backups, keep but warehouse)
     - 25TB junk (old temp files, can delete)

3. **Strategy** (Week 5-6):
   - **Critical (5TB)**: Auto-migrate to NAS
   - **Useful (20TB)**: Auto-migrate to NAS
   - **Archive (30TB)**: Warehouse on 8 disks
   - **Junk (25TB)**: Delete

4. **Execution** (Week 6-8):
   - Migrate 25TB to NAS (critical + useful)
   - Consolidate archive to 8 disks:
     - Copy files from 12 disks → 8 disks (consolidate)
     - Label disks: ARCHIVE-001 through ARCHIVE-008
     - Store in labeled boxes
   - Wipe 12 disks (can reuse)

5. **Warehousing** (Week 8):
   - For each archive disk:
     - Generate label
     - Print and affix
     - Store in physical location
     - System tracks: "File X on disk ARCHIVE-003, Shelf B, Box 5"

6. **Result**:
   - 25TB on NAS (accessible)
   - 30TB warehoused (8 labeled disks)
   - 25TB deleted (freed space)
   - 12 disks wiped and ready for reuse
   - Full catalog searchable (even warehoused files)

---

## UI/UX Design

### Dashboard

```
┌─────────────────────────────────────────────────────────────┐
│  Archivum Dashboard                                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  📊 Overview                                                 │
│  ├─ Total Files: 16.2M                                       │
│  ├─ Total Size: 80.5 TB                                      │
│  ├─ Sources: 20 disks scanned                                │
│  └─ Duplicates: 4.5M files (30.2 TB can be saved)           │
│                                                              │
│  🎯 Migration Status                                         │
│  ├─ ✅ Migrated: 5.2M files (18.3 TB)                        │
│  ├─ ⏳ In Progress: 1.1M files (4.2 TB)                      │
│  ├─ 📋 Queued: 3.8M files (12.5 TB)                          │
│  └─ ⚠️  Needs Review: 450K files (2.1 TB)                    │
│                                                              │
│  🔍 Classification                                           │
│  ├─ ✅ Classified: 15.1M files (94%)                         │
│  ├─ 🤖 AI Suggested: 650K files (need approval)             │
│  └─ ❓ Unknown: 450K files (need manual review)             │
│                                                              │
│  📦 Warehouse                                                │
│  ├─ Disks: 5 labeled and stored                             │
│  ├─ Files: 2.1M (10.2 TB)                                    │
│  └─ Location: Tracked in system                             │
│                                                              │
│  [View Detailed Reports] [Start Migration] [Review Tasks]   │
└─────────────────────────────────────────────────────────────┘
```

### Migration Planning View

```
┌─────────────────────────────────────────────────────────────┐
│  Migration Plan                                [Auto-Plan]   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Filter: [All Zones ▼] [All Strategies ▼] [Search...]       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Strategy: AUTO_COPY                         [Expand] │   │
│  │ Files: 8.2M | Size: 32.5 TB                          │   │
│  │                                                       │   │
│  │ Destination Mappings:                                │   │
│  │ ┌────────────────────────┬──────────────────────┐    │   │
│  │ │ Source                 │ Destination          │    │   │
│  │ ├────────────────────────┼──────────────────────┤    │   │
│  │ │ Documents/*.pdf        │ /NAS/Private/Docs/   │    │   │
│  │ │ Photos/**/*.jpg        │ /NAS/Private/Photos/ │    │   │
│  │ │ Books/**/*.epub        │ /NAS/Shared/Books/   │    │   │
│  │ └────────────────────────┴──────────────────────┘    │   │
│  │                                                       │   │
│  │ [Review Details] [Modify] [Add to Queue]             │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Strategy: GIT_ARCHIVE                       [Expand] │   │
│  │ Projects: 150 | Total Size: 8.5 GB                   │   │
│  │                                                       │   │
│  │ Status:                                               │   │
│  │ ├─ ✅ Complete: 80 projects                           │   │
│  │ ├─ ⏳ In Progress: 15 projects                        │   │
│  │ └─ 📋 Pending: 55 projects (need Git URLs)           │   │
│  │                                                       │   │
│  │ [View Projects] [Setup Git URLs]                     │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Strategy: WAREHOUSE                         [Expand] │   │
│  │ Disks: 5 | Files: 2.1M | Size: 10.2 TB               │   │
│  │                                                       │   │
│  │ Disks to Warehouse:                                   │   │
│  │ ├─ WD-BLUE-001: 450K files (4.1 TB) ✅ Labeled        │   │
│  │ ├─ WD-BLACK-002: 380K files (3.8 TB) ⏳ Needs label   │   │
│  │ └─ SEAGATE-003: 220K files (2.3 TB) ⏳ Needs label    │   │
│  │                                                       │   │
│  │ [Label Disks] [Print Labels] [Mark Stored]           │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  [Execute All Queued] [Export Plan] [Save Draft]            │
└─────────────────────────────────────────────────────────────┘
```

### Code Project Git Archive Setup

```
┌─────────────────────────────────────────────────────────────┐
│  Archive Code Project to Git                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Project: my-api                                             │
│  Type: Maven (com.example:my-api:2.0.0)                     │
│  Path: /mnt/disk1/projects/my-api                            │
│  Size: 125 MB (1,250 files)                                  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Step 1: Create Git Repository (Manual)               │   │
│  │                                                       │   │
│  │ Create a Git repository on your Git hosting service: │   │
│  │ • GitHub: https://github.com/new                     │   │
│  │ • GitLab: https://gitlab.com/projects/new            │   │
│  │ • Gitea: Your self-hosted instance                   │   │
│  │                                                       │   │
│  │ Repository name suggestion: my-api                    │   │
│  │                                                       │   │
│  │ ☐ I have created the Git repository                  │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Step 2: Enter Git URL                                │   │
│  │                                                       │   │
│  │ Git URL (SSH or HTTPS):                              │   │
│  │ [git@github.com:username/my-api.git               ]  │   │
│  │                                                       │   │
│  │ Branch (optional): [main                           ]  │   │
│  │                                                       │   │
│  │ Notes (optional):                                     │   │
│  │ [Production version, stable release 2.0.0          ]  │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Step 3: System Actions                               │   │
│  │                                                       │   │
│  │ Archivum will:                                        │   │
│  │ ✓ Copy project to staging: /mnt/staging/my-api      │   │
│  │ ✓ Initialize Git (if needed)                         │   │
│  │ ✓ Add remote: origin → [your URL]                    │   │
│  │ ✓ Stage all files                                     │   │
│  │ ⏸ Wait for you to push                               │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Step 4: Manual Push (Your Action)                    │   │
│  │                                                       │   │
│  │ After clicking "Prepare for Git", run these commands:│   │
│  │                                                       │   │
│  │   cd /mnt/staging/my-api                             │   │
│  │   git commit -m "Archive from external disk"         │   │
│  │   git push origin main                               │   │
│  │                                                       │   │
│  │ Then return here and click "Mark Complete"           │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  [Cancel] [Save for Later] [Prepare for Git Push]           │
└─────────────────────────────────────────────────────────────┘
```

### Warehouse Disk Label Setup

```
┌─────────────────────────────────────────────────────────────┐
│  Warehouse External Disk                                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Disk: WD Blue 4TB                                           │
│  Files: 1.25M | Size: 3.8 TB                                 │
│  Source: /mnt/disk3                                          │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Disk Identification                                   │   │
│  │                                                       │   │
│  │ Disk Label (required):                               │   │
│  │ [WD-BLUE-001                                       ]  │   │
│  │ ↑ Unique identifier for this disk                    │   │
│  │                                                       │   │
│  │ Physical Description:                                 │   │
│  │ [4TB WD Blue External HDD, Serial: WXY123...       ]  │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Storage Location                                      │   │
│  │                                                       │   │
│  │ Location (required):                                  │   │
│  │ [Shelf A, Box 2                                    ]  │   │
│  │                                                       │   │
│  │ Container:                                            │   │
│  │ [Plastic storage box with label                   ]  │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Retention Policy                                      │   │
│  │                                                       │   │
│  │ Keep until: [Forever ▼]                              │   │
│  │                                                       │   │
│  │ Verify every: [1 year ▼]                             │   │
│  │ Next verification: 2026-12-29                         │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Notes                                                 │   │
│  │                                                       │   │
│  │ [Old backups from 2010-2015. Contains family       ]  │   │
│  │ [photos (RAW files), old work projects, tax        ]  │   │
│  │ [documents. Low priority but keep for reference.   ]  │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Preview: Physical Label                              │   │
│  │                                                       │   │
│  │  ╔═══════════════════════════════════════════════╗   │   │
│  │  ║  WD-BLUE-001                                  ║   │   │
│  │  ║  4TB WD Blue External HDD                     ║   │   │
│  │  ║  Old Backups 2010-2015                        ║   │   │
│  │  ║                                               ║   │   │
│  │  ║  Location: Shelf A, Box 2                     ║   │   │
│  │  ║  Files: 1.25M | Size: 3.8TB                   ║   │   │
│  │  ║  Cataloged: 2025-12-29                        ║   │   │
│  │  ║                                               ║   │   │
│  │  ║  ▐ ▌▐ ▌▐ ▌▐ ▌ (Barcode: WD-BLUE-001)        ║   │   │
│  │  ╚═══════════════════════════════════════════════╝   │   │
│  │                                                       │   │
│  │  [Edit Label Design] [Print Label]                   │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  [Cancel] [Save Draft] [Catalog & Print Label]              │
└─────────────────────────────────────────────────────────────┘
```

---

## Open Questions

### 1. File Deletion After Migration

**Question**: After successfully migrating files to NAS, should original files be deleted automatically?

**Options**:
- **A**: Keep originals (user deletes manually later)
- **B**: Delete automatically after verification
- **C**: User choice per migration (checkbox: "Delete after migration")
- **D**: Quarantine for 30 days, then delete

**Recommendation**: **Option C** - Give user control per migration strategy

---

### 2. Partial Migrations

**Question**: Can user migrate files incrementally (e.g., migrate documents first, media later)?

**Options**:
- **A**: Yes, fully flexible (migrate any subset at any time)
- **B**: Must migrate by zone (all documents at once)
- **C**: Must migrate by disk (all files from one disk)

**Recommendation**: **Option A** - Maximum flexibility

---

### 3. Re-scanning After Migration

**Question**: If user plugs disk back in after migration, should system:

**Options**:
- **A**: Re-scan and detect duplicates (warn: "Already on NAS")
- **B**: Remember disk ID, show previous scan results
- **C**: Ignore (assume user knows)

**Recommendation**: **Option B** - Track disk IDs, show status

---

### 4. Code Project: Multiple Branches vs Multiple Repos

**Question**: For code projects with multiple versions, should they go to:

**Options**:
- **A**: Same repo, different branches (e.g., v1.0-archive, v2.0-archive)
- **B**: Different repos (e.g., my-api-v1, my-api-v2)
- **C**: User choice

**Recommendation**: **Option C** - Let user decide per project

---

### 5. Warehouse Disk Verification

**Question**: Should system periodically verify warehoused disks are still readable?

**Options**:
- **A**: No, trust user to manage
- **B**: Yes, remind user to plug in and verify yearly
- **C**: Yes, auto-verify if disk is plugged in

**Recommendation**: **Option B** - Remind user, track verification dates

---

### 6. Migration Rollback

**Question**: If migration fails or user changes mind, should system support rollback?

**Options**:
- **A**: No rollback (migrations are final)
- **B**: Rollback within 24 hours
- **C**: Keep originals, user can re-migrate

**Recommendation**: **Option C** - Don't delete originals until user confirms

---

### 7. Shared vs Private Classification

**Question**: How does system determine if file goes to Shared or Private NAS folder?

**Options**:
- **A**: User sets per-file or per-folder
- **B**: AI suggests based on content (e.g., "Work" → Private, "Family" → Shared)
- **C**: Default all to Private, user moves to Shared manually
- **D**: Zone-based rules (e.g., SOFTWARE → Shared, DOCUMENTS → Private)

**Recommendation**: **Option D** with user overrides

---

### 8. Migration Queue Priority

**Question**: Can user prioritize migrations (e.g., migrate critical documents first)?

**Options**:
- **A**: FIFO (first in, first out)
- **B**: User sets priority (high/medium/low)
- **C**: Automatic priority (small files first, then large)

**Recommendation**: **Option B** - User control

---

### 9. Network Interruption Handling

**Question**: If network drops during migration to NAS:

**Options**:
- **A**: Fail, user restarts manually
- **B**: Retry automatically (3 attempts)
- **C**: Resume from last checkpoint

**Recommendation**: **Option C** - Resume support for large migrations

---

### 10. Multi-User Support

**Question**: Can multiple family members have separate classifications/migrations?

**Options**:
- **A**: Single user system
- **B**: Multi-user with separate destinations (e.g., /Private/haris/, /Private/maria/)
- **C**: Multi-user with shared classifications

**Recommendation**: **Option B** for future, start with **Option A**

---

### 11. Source Disk Cleanup After Migration

**Question**: How to clean up source disks after migration?

**User requirement**:
- Work disk-by-disk
- Wait 3 days after migration (for NAS backups)
- User gets email if backup fails
- Format entire disk (don't selectively delete files)
- Move to next disk

**Solution**: **Disk-level formatting with retention period**

**Workflow per disk**:
1. Plug disk, scan, migrate all files
2. Mark source as "Migration Complete"
3. Wait 3 days (= 3 nightly NAS backups)
4. User gets email if any backup fails
5. Unless user clicks "Hold Cleanup", disk is safe to format
6. User formats entire disk (wipe everything)
7. Disk ready for reuse or storage
8. Move to next disk

**Settings**:
```
Cleanup Policy:
├─ Retention before format: [3 days ▼]
├─ Email on backup failure: [✓] Enabled
└─ Auto-notify when safe to format: [✓] Enabled
```

**Timeline Example (per disk)**:
```
Day 1 (Dec 27):
- Plug "WD Blue 4TB"
- Scan, migrate all files to NAS/Warehouse
- Mark as "Complete"

Night Dec 27: NAS backup #1
Night Dec 28: NAS backup #2
Night Dec 29: NAS backup #3

Day 4 (Dec 30):
- System shows: "WD Blue 4TB - Safe to format"
- User formats disk
- Disk wiped, ready for reuse

Day 5 (Dec 31):
- Plug next disk "Seagate 4TB"
- Repeat process
```

**UI**:
```
┌─────────────────────────────────────────────────────────────┐
│  Source Disks - Cleanup Status                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✓ WD Blue 4TB                                        │   │
│  │   Migration completed: Dec 27, 2025                  │   │
│  │   Files migrated: 850 (2.1 TB)                       │   │
│  │   Status: ✓ Safe to format (3+ days elapsed)         │   │
│  │   Last NAS backup: Dec 29, 02:00 ✓                   │   │
│  │                                                       │   │
│  │   [Mark as Formatted] [Format Now (if plugged)]      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ⏳ Seagate 4TB                                        │   │
│  │   Migration completed: Dec 29, 2025                  │   │
│  │   Files migrated: 1,200 (3.5 TB)                     │   │
│  │   Status: ⏳ Wait 1 more day before formatting       │   │
│  │   Safe to format after: Jan 1, 2026                  │   │
│  │                                                       │   │
│  │   [Hold Cleanup] [Override & Format Now]             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ⚠️  WD Black 4TB                                      │   │
│  │   Migration completed: Dec 28, 2025                  │   │
│  │   Files migrated: 2,400 (5.8 TB)                     │   │
│  │   Status: ⚠️ CLEANUP ON HOLD (user action)           │   │
│  │   Reason: Backup failed on Dec 29 (see email)        │   │
│  │                                                       │   │
│  │   [Resume Cleanup] [Format Anyway]                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Settings:                                                   │
│  ├─ Retention before format: [3 days ▼]                     │
│  ├─ Email on backup failure: [✓] Enabled                    │
│  └─ Auto-hold on backup failure: [✓] Enabled                │
└─────────────────────────────────────────────────────────────┘
```

**Benefits**:
- ✅ Simple (format entire disk, no selective deletion)
- ✅ Fast (no need to plug disk back in to delete files)
- ✅ Safe (3-day retention, 3 backups)
- ✅ User control ("Hold Cleanup" if backup failed)
- ✅ Clean slate for each disk
- ✅ Work disk-by-disk at your own pace

**No need to**:
- Plug disk back in
- Selectively delete files
- Track individual file deletion
- Worry about missed files

**Just**:
- Wait 3 days
- Format disk (when ready)
- Done!

**Recommendation**: Keep it simple - disk-level formatting after retention period

---

### 12. WAREHOUSE Strategy - Clarification

**Question**: What is WAREHOUSE and when to use it?

**CORRECTED Concept**: **Consolidate files TO organized warehouse disks** (not just catalog existing disks)

**Use case**: Consolidate scattered files from multiple messy disks to fewer, organized warehouse disks

**Problem**: User has 20 disks with files scattered everywhere:
- Duplicates across multiple disks
- Poor organization
- Want some files accessible but not on expensive NAS

**Solution**: WAREHOUSE migration strategy
1. **Scan all source disks** (DISCOVERY)
2. **Classify**: 20TB hot (→ NAS), 60TB cold (→ Warehouse)
3. **System plans warehouse consolidation**:
   - After dedup: 60TB → 40TB (save space)
   - Suggests: 10 warehouse disks × 4TB
   - Organizes by category
4. **User provides warehouse disks** (empty or to be wiped)
5. **System executes**:
   - Copies cold files to warehouse disks
   - Deduplicates during copy
   - Organizes logically
   - Generates printable labels
6. **Result**:
   - 10 clean, labeled warehouse disks
   - Cataloged, searchable
   - Original 20 disks can be formatted and reused

**Later retrieval**:
- Search: "project-backup-2012.zip"
- System: "Found on WAREHOUSE-001, Shelf A, Box 1"
- User retrieves disk, plugs in, gets file

**Benefits**:
- ✅ Consolidation (20 messy disks → 10 clean warehouse disks)
- ✅ Deduplication (60TB → 40TB)
- ✅ Organization (by category, labeled)
- ✅ Searchable (cataloged in database)
- ✅ Saves NAS space (cold data not on NAS)
- ✅ Cheap storage (external HDDs)
- ✅ Can reuse original disks (10 freed up)

**Example warehouse organization**:
```
WAREHOUSE-001: Old backups 2010-2012 (4TB)
WAREHOUSE-002: Old backups 2013-2015 (4TB)
WAREHOUSE-003: Software installers archive (4TB)
WAREHOUSE-004: Raw footage 2015-2018 (4TB)
WAREHOUSE-005: Completed projects archives (4TB)
... etc
```

**When to use WAREHOUSE**:
- Cold data (rarely accessed)
- Old backups and archives
- Completed projects (historical reference)
- Raw media (before editing)
- Software installers (large collection)
- Want organization without NAS cost

**When to use NAS MIGRATION**:
- Hot data (frequently accessed)
- Important documents
- Active photo/video collection
- Current projects
- Need fast access

**Key difference from original understanding**:
- ❌ Old: Just catalog existing disks (files stay where they are)
- ✅ New: **Consolidate and migrate** files TO organized warehouse disks (active migration strategy)

---

## Next Steps

1. **Review this document**
   - User feedback on workflows
   - Identify missing scenarios
   - Prioritize strategies

2. **Finalize requirements**
   - Answer open questions
   - Define must-have vs nice-to-have features
   - Set scope for MVP (Minimum Viable Product)

3. **Design database schema**
   - Migration plans
   - Execution tracking
   - Warehouse metadata

4. **Design API**
   - Migration endpoints
   - WebSocket progress updates
   - Execution control

5. **Design UI mockups**
   - Migration planning view
   - Progress monitoring
   - Git archive workflow
   - Warehouse disk labeling

6. **Create implementation plan**
   - Break into milestones
   - Estimate effort
   - Prioritize features

---

**Let's discuss and refine this plan before writing any code!**
