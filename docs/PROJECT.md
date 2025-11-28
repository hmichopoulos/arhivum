# Archivum - Project Overview

## What is Archivum?

Archivum is a personal file organization toolkit designed to help organize large, scattered file collections into a clean, searchable archive.

## The Problem

Over years of digital life, files accumulate:
- ~20 external HDDs with backups of backups
- Multiple cloud services (OneDrive, Google Drive, Dropbox, etc.)
- Duplicates everywhere
- No consistent organization
- Difficult to find anything
- Wasted storage space

## The Solution

Archivum provides:

1. **Unified Catalog** — Scan all sources, build one searchable index
2. **Smart Deduplication** — Find duplicates while respecting software integrity
3. **Assisted Classification** — Rules + AI to categorize files automatically
4. **Manual Review** — UI for human decisions on ambiguous cases
5. **Safe Migration** — Move files to final structure with verification

## Components

### archivum-scanner

Lightweight CLI that runs on your computer where disks are plugged in.

```bash
# Scan a disk
archivum scan /media/disk1 --name "Backup-2018"

# Copy unique files to staging
archivum copy /media/disk1 --to-staging
```

**Responsibilities:**
- Walk filesystem, collect metadata
- Compute SHA-256 hashes
- Send results to server via API
- Copy files to NAS staging area

### archivum-server

Spring Boot application running on a VM.

**Responsibilities:**
- Store catalog in PostgreSQL
- Analyze duplicates (file-level and folder-level)
- Run classification rules
- Call Claude API for ambiguous files
- Orchestrate migration
- Serve REST API for UI

### archivum-ui

React web application for review and management.

**Features:**
- Browse scanned sources
- Override zone classifications
- Review duplicate groups
- Classify ambiguous files
- Manage archive structure
- Define classification rules
- Monitor scan/migration progress

## Target Folder Structure

```
/Archive
├── /Life                    # Personal memories
│   ├── /Daily               # Stream of everyday photos (by year/month)
│   └── /Events              # Named occasions (vacations, birthdays)
│
├── /Work                    # Professional projects (by year + name)
│
├── /Documents               # Administrative files
│   ├── /Family              # Shared (house, vehicles, subscriptions)
│   ├── /[PersonName]        # Per-person (identity, medical, employment)
│   └── /Manuals             # Product manuals
│
├── /Books                   # Ebooks by subject
│   ├── /Fiction
│   └── /Technical
│
├── /Media                   # Entertainment
│   ├── /Movies
│   ├── /TV-Shows
│   ├── /Music
│   └── /Funny-Videos
│
├── /Software                # Installers, games, tools
│
├── /Reference               # Functional photos (not memories)
│   ├── /Equipment
│   ├── /Receipts
│   ├── /Screenshots
│   └── /Home
│
├── /Interests               # Hobbies (recipes, collections)
│
├── /Backups                 # Raw device backups (untouched)
│
└── /Inbox                   # Triage area for new/unsorted files
```

## Workflow

```
┌─────────────┐
│ Plug in     │
│ external    │
│ disk        │
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│ Run         │────▶│ Archivum    │
│ archivum    │     │ Server      │
│ scanner     │     │ (catalog)   │
└──────┬──────┘     └─────────────┘
       │
       ▼
┌─────────────┐
│ Copy to     │
│ NAS staging │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Review in   │
│ Web UI      │
│             │
│ • Zones     │
│ • Duplicates│
│ • Classify  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Migrate to  │
│ /Archive    │
└─────────────┘
```

## Key Design Decisions

### 1. Scanning happens locally

Disks are plugged into your computer, not the server. The scanner is lightweight and sends only metadata to the server.

### 2. Zone-based deduplication

Files are categorized into zones (MEDIA, SOFTWARE, etc.) that determine how deduplication works. Software zones never have individual files deduplicated — only complete folders.

### 3. Nothing deleted without approval

All destructive operations require explicit user confirmation in the UI. Duplicates are marked, not automatically removed.

### 4. Preserve existing organization

If you've already organized some folders (e.g., `/Photos/2015-Vacation-Italy/`), the toolkit detects and preserves this structure.

### 5. Folder structure first, tools later

The archive is organized into plain folders. Tools like Immich (photos) or Calibre (books) can be added later, pointing to these folders.

## Timeline

| Phase | What | Duration |
|-------|------|----------|
| 1 | Core Scanner + Server + Dedup | 3-4 weeks |
| 2 | Classification + Basic UI | 3-4 weeks |
| 3 | Full UI + Migration | 2-3 weeks |
| 4 | Polish + Photo Watcher | 1-2 weeks |

**Total: ~10-12 weeks** of part-time development

Scanning 20 disks happens in parallel with development, taking 4-6 weeks of background processing.

## Success Criteria

1. All files from all sources cataloged in one database
2. Duplicates identified and resolved (estimated 30-50% space savings)
3. All files organized into the target folder structure
4. Easy to find any file via search or browsing
5. New files (from phone uploads) automatically organized
6. Old disks can be safely reformatted or archived
