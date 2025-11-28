# Database Schema

## Overview

Archivum uses PostgreSQL to store the catalog of all scanned files, their relationships, and processing state.

## Entity Relationship Diagram

```
┌─────────────────┐       ┌─────────────────┐
│     Source      │       │   FileHash      │
├─────────────────┤       ├─────────────────┤
│ id              │       │ id              │
│ name            │       │ sha256          │◄──────────────────────┐
│ type            │       │ size            │                       │
│ status          │       │ first_seen_at   │                       │
│ scanned_at      │       └─────────────────┘                       │
│ total_files     │                                                 │
│ total_size      │                                                 │
└────────┬────────┘                                                 │
         │                                                          │
         │ 1:N                                                      │
         ▼                                                          │
┌─────────────────┐       ┌─────────────────┐       ┌───────────────┴─┐
│  ScannedFile    │       │ DuplicateGroup  │       │ ScannedFile     │
├─────────────────┤       ├─────────────────┤       │ (hash reference)│
│ id              │       │ id              │       └─────────────────┘
│ source_id       │──────►│ hash_id         │
│ original_path   │       │ file_count      │
│ filename        │       │ total_size      │
│ extension       │       │ status          │
│ size            │       │ kept_file_id    │
│ hash_id         │───────┤ reviewed_at     │
│ zone            │       └─────────────────┘
│ classification  │
│ status          │
│ created_at      │
│ modified_at     │
│ exif_date       │
│ staged_path     │
│ archive_path    │
└─────────────────┘

┌─────────────────┐       ┌─────────────────┐
│ FolderSummary   │       │FolderDuplicate  │
├─────────────────┤       │     Group       │
│ id              │       ├─────────────────┤
│ source_id       │       │ id              │
│ path            │       │ similarity      │
│ zone            │       │ status          │
│ is_software_root│       │ reviewed_at     │
│ content_hash    │       └────────┬────────┘
│ file_count      │                │
│ total_size      │                │ 1:N
└─────────────────┘                ▼
                          ┌─────────────────┐
                          │FolderDuplicate  │
                          │    Member       │
                          ├─────────────────┤
                          │ id              │
                          │ group_id        │
                          │ folder_id       │
                          │ is_kept         │
                          └─────────────────┘

┌─────────────────┐       ┌─────────────────┐
│Classification   │       │  ArchiveFolder  │
│     Rule        │       ├─────────────────┤
├─────────────────┤       │ id              │
│ id              │       │ path            │
│ name            │       │ name            │
│ priority        │       │ parent_id       │
│ conditions      │       │ default_zone    │
│ classification  │       │ created_at      │
│ destination     │       └─────────────────┘
│ enabled         │
└─────────────────┘

┌─────────────────┐
│  MigrationJob   │
├─────────────────┤
│ id              │
│ status          │
│ total_files     │
│ processed_files │
│ failed_files    │
│ started_at      │
│ completed_at    │
│ error_message   │
└─────────────────┘
```

## Tables

### source

Represents a scanned disk or cloud account.

```sql
CREATE TABLE source (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,          -- "Backup-2018", "Google Drive"
    type            VARCHAR(50) NOT NULL,           -- DISK, CLOUD_GDRIVE, CLOUD_ONEDRIVE, etc.
    status          VARCHAR(50) NOT NULL,           -- PENDING, SCANNING, COMPLETED, FAILED
    root_path       VARCHAR(1000),                  -- Original mount path during scan
    scanned_at      TIMESTAMP,
    total_files     BIGINT DEFAULT 0,
    total_size      BIGINT DEFAULT 0,               -- In bytes
    unique_files    BIGINT DEFAULT 0,               -- After dedup
    unique_size     BIGINT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT source_name_unique UNIQUE (name)
);

CREATE INDEX idx_source_status ON source(status);
```

### file_hash

Stores unique file hashes. Multiple files can reference the same hash (duplicates).

```sql
CREATE TABLE file_hash (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sha256          CHAR(64) NOT NULL,              -- SHA-256 hash (hex)
    size            BIGINT NOT NULL,                -- File size in bytes
    first_seen_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT file_hash_sha256_unique UNIQUE (sha256)
);

CREATE INDEX idx_file_hash_sha256 ON file_hash(sha256);
```

### scanned_file

Every file discovered during scanning.

```sql
CREATE TABLE scanned_file (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID NOT NULL REFERENCES source(id),
    hash_id         UUID REFERENCES file_hash(id),  -- NULL until hashed
    
    -- Original location
    original_path   VARCHAR(4000) NOT NULL,         -- Full path on source
    filename        VARCHAR(255) NOT NULL,
    extension       VARCHAR(50),                    -- Lowercase, without dot
    
    -- File properties
    size            BIGINT NOT NULL,
    created_at_source   TIMESTAMP,                  -- File creation time on source
    modified_at_source  TIMESTAMP,                  -- File modification time on source
    
    -- Extracted metadata
    mime_type       VARCHAR(100),
    exif_date       TIMESTAMP,                      -- For photos: EXIF date taken
    exif_location   GEOGRAPHY(POINT, 4326),         -- For photos: GPS coordinates
    metadata_json   JSONB,                          -- Additional metadata
    
    -- Classification
    zone            VARCHAR(50),                    -- MEDIA, DOCUMENTS, SOFTWARE, etc.
    zone_manual     BOOLEAN DEFAULT FALSE,          -- Was zone manually set?
    classification  VARCHAR(100),                   -- DAILY_PHOTO, EVENT_PHOTO, BOOK, etc.
    classification_confidence DECIMAL(3,2),         -- 0.00 to 1.00
    classification_manual BOOLEAN DEFAULT FALSE,
    
    -- Target location
    target_folder_id UUID REFERENCES archive_folder(id),
    archive_path    VARCHAR(4000),                  -- Final path in /Archive
    
    -- Processing state
    status          VARCHAR(50) NOT NULL DEFAULT 'DISCOVERED',
    -- DISCOVERED → HASHED → ANALYZED → CLASSIFIED → STAGED → MIGRATED
    -- Also: DUPLICATE, SKIPPED, FAILED
    
    staged_path     VARCHAR(4000),                  -- Path in staging area
    staged_at       TIMESTAMP,
    migrated_at     TIMESTAMP,
    error_message   TEXT,
    
    -- Timestamps
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT scanned_file_source_path_unique UNIQUE (source_id, original_path)
);

CREATE INDEX idx_scanned_file_source ON scanned_file(source_id);
CREATE INDEX idx_scanned_file_hash ON scanned_file(hash_id);
CREATE INDEX idx_scanned_file_status ON scanned_file(status);
CREATE INDEX idx_scanned_file_zone ON scanned_file(zone);
CREATE INDEX idx_scanned_file_classification ON scanned_file(classification);
CREATE INDEX idx_scanned_file_extension ON scanned_file(extension);
```

### duplicate_group

Groups of files that have the same hash.

```sql
CREATE TABLE duplicate_group (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hash_id         UUID NOT NULL REFERENCES file_hash(id),
    file_count      INTEGER NOT NULL,
    total_size      BIGINT NOT NULL,                -- file_count * size
    wasted_size     BIGINT NOT NULL,                -- (file_count - 1) * size
    
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING, REVIEWED, RESOLVED, IGNORED
    
    kept_file_id    UUID REFERENCES scanned_file(id),  -- Which copy to keep
    reviewed_at     TIMESTAMP,
    reviewed_by     VARCHAR(100),                   -- 'AUTO' or username
    
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT duplicate_group_hash_unique UNIQUE (hash_id)
);

CREATE INDEX idx_duplicate_group_status ON duplicate_group(status);
CREATE INDEX idx_duplicate_group_wasted ON duplicate_group(wasted_size DESC);
```

### folder_summary

Aggregated information about folders, used for zone detection and folder-level dedup.

```sql
CREATE TABLE folder_summary (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID NOT NULL REFERENCES source(id),
    path            VARCHAR(4000) NOT NULL,
    
    -- Zone detection
    zone            VARCHAR(50),
    zone_manual     BOOLEAN DEFAULT FALSE,
    is_software_root BOOLEAN DEFAULT FALSE,
    software_root_marker VARCHAR(100),              -- "Setup.exe", ".git", etc.
    
    -- Content summary
    file_count      INTEGER NOT NULL DEFAULT 0,
    total_size      BIGINT NOT NULL DEFAULT 0,
    content_hash    CHAR(64),                       -- Hash of all file hashes (sorted)
    
    -- File type breakdown (for zone detection)
    image_count     INTEGER DEFAULT 0,
    video_count     INTEGER DEFAULT 0,
    document_count  INTEGER DEFAULT 0,
    audio_count     INTEGER DEFAULT 0,
    executable_count INTEGER DEFAULT 0,
    archive_count   INTEGER DEFAULT 0,
    
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT folder_summary_source_path_unique UNIQUE (source_id, path)
);

CREATE INDEX idx_folder_summary_source ON folder_summary(source_id);
CREATE INDEX idx_folder_summary_zone ON folder_summary(zone);
CREATE INDEX idx_folder_summary_content_hash ON folder_summary(content_hash);
```

### folder_duplicate_group

Groups of folders with similar or identical content.

```sql
CREATE TABLE folder_duplicate_group (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    similarity      DECIMAL(5,2) NOT NULL,          -- 0.00 to 100.00 percent
    
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    action          VARCHAR(50),                    -- MERGE, KEEP_ALL, IGNORE
    reviewed_at     TIMESTAMP,
    
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE folder_duplicate_member (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES folder_duplicate_group(id),
    folder_id       UUID NOT NULL REFERENCES folder_summary(id),
    is_kept         BOOLEAN DEFAULT FALSE,
    unique_files    INTEGER DEFAULT 0,              -- Files only in this copy
    unique_size     BIGINT DEFAULT 0,
    
    CONSTRAINT folder_dup_member_unique UNIQUE (group_id, folder_id)
);

CREATE INDEX idx_folder_dup_member_group ON folder_duplicate_member(group_id);
```

### classification_rule

User-defined rules for automatic classification.

```sql
CREATE TABLE classification_rule (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    priority        INTEGER NOT NULL DEFAULT 100,   -- Lower = higher priority
    enabled         BOOLEAN DEFAULT TRUE,
    
    -- Conditions (JSON for flexibility)
    conditions      JSONB NOT NULL,
    /*
    Example:
    {
      "operator": "AND",
      "rules": [
        {"field": "original_path", "op": "contains", "value": "vacation"},
        {"field": "extension", "op": "in", "value": ["jpg", "jpeg", "png"]}
      ]
    }
    */
    
    -- Action
    classification  VARCHAR(100) NOT NULL,          -- EVENT_PHOTO, DAILY_PHOTO, etc.
    target_folder   VARCHAR(4000),                  -- e.g., "/Life/Events/{extracted_name}"
    
    -- Stats
    matches_count   BIGINT DEFAULT 0,
    last_matched_at TIMESTAMP,
    
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_classification_rule_priority ON classification_rule(priority);
CREATE INDEX idx_classification_rule_enabled ON classification_rule(enabled);
```

### archive_folder

The target folder structure.

```sql
CREATE TABLE archive_folder (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path            VARCHAR(4000) NOT NULL,         -- Full path from /Archive
    name            VARCHAR(255) NOT NULL,          -- Folder name only
    parent_id       UUID REFERENCES archive_folder(id),
    
    default_zone    VARCHAR(50),                    -- Default zone for files in this folder
    folder_type     VARCHAR(50),                    -- LIFE_DAILY, LIFE_EVENT, WORK, etc.
    
    -- For event folders
    event_start_date DATE,
    event_end_date   DATE,
    
    file_count      INTEGER DEFAULT 0,
    total_size      BIGINT DEFAULT 0,
    
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT archive_folder_path_unique UNIQUE (path)
);

CREATE INDEX idx_archive_folder_parent ON archive_folder(parent_id);
CREATE INDEX idx_archive_folder_type ON archive_folder(folder_type);
```

### migration_job

Tracks file migration batches.

```sql
CREATE TABLE migration_job (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    
    total_files     INTEGER NOT NULL DEFAULT 0,
    processed_files INTEGER NOT NULL DEFAULT 0,
    failed_files    INTEGER NOT NULL DEFAULT 0,
    total_size      BIGINT NOT NULL DEFAULT 0,
    processed_size  BIGINT NOT NULL DEFAULT 0,
    
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE migration_job_file (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_job(id),
    file_id         UUID NOT NULL REFERENCES scanned_file(id),
    
    source_path     VARCHAR(4000) NOT NULL,         -- Where file is now (staging)
    target_path     VARCHAR(4000) NOT NULL,         -- Where file should go
    
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING, COPYING, VERIFYING, COMPLETED, FAILED
    
    verified_hash   CHAR(64),                       -- Hash after copy (for verification)
    error_message   TEXT,
    
    processed_at    TIMESTAMP
);

CREATE INDEX idx_migration_job_file_job ON migration_job_file(job_id);
CREATE INDEX idx_migration_job_file_status ON migration_job_file(status);
```

## Enums (as VARCHAR with constraints)

### Source Type
- `DISK` — External disk
- `CLOUD_GDRIVE` — Google Drive
- `CLOUD_ONEDRIVE` — OneDrive
- `CLOUD_DROPBOX` — Dropbox
- `CLOUD_ICLOUD` — iCloud
- `SSH` — SSH/SFTP server

### Zone
- `MEDIA` — Photos, videos, music
- `DOCUMENTS` — PDFs, Office files
- `BOOKS` — Ebooks
- `SOFTWARE` — Installers, applications
- `BACKUP` — Full backup sets
- `CODE` — Source code repositories
- `UNKNOWN` — Needs classification

### File Status
- `DISCOVERED` — Found during scan, not yet hashed
- `HASHED` — Hash computed
- `ANALYZED` — Duplicate analysis complete
- `CLASSIFIED` — Classification assigned
- `STAGED` — Copied to staging area
- `MIGRATED` — Moved to final location
- `DUPLICATE` — Marked as duplicate (another copy kept)
- `SKIPPED` — Intentionally skipped
- `FAILED` — Processing failed

### Classification
- `DAILY_PHOTO` — Regular daily photo
- `EVENT_PHOTO` — Photo from named event
- `REFERENCE_PHOTO` — Functional/documentation photo
- `DOCUMENT_PERSONAL` — Personal document
- `DOCUMENT_FAMILY` — Family/household document
- `DOCUMENT_WORK` — Work document
- `BOOK_FICTION` — Fiction ebook
- `BOOK_TECHNICAL` — Technical ebook
- `VIDEO_PERSONAL` — Personal video
- `VIDEO_MEDIA` — Movie/TV show
- `MUSIC` — Music file
- `SOFTWARE` — Software/installer
- `BACKUP` — Raw backup content

## Indexes Summary

Key indexes for performance:

1. **Duplicate detection:** `file_hash.sha256`, `scanned_file.hash_id`
2. **Source browsing:** `scanned_file.source_id`, `folder_summary.source_id`
3. **Status tracking:** `scanned_file.status`, `duplicate_group.status`
4. **Classification:** `scanned_file.zone`, `scanned_file.classification`
5. **Folder dedup:** `folder_summary.content_hash`

## Migrations

Use Flyway for database migrations. Files in `src/main/resources/db/migration/`:

```
V001__initial_schema.sql      -- Create all tables
V002__add_zone_detection.sql  -- Add zone-related columns
V003__add_classification.sql  -- Add classification tables
...
```
