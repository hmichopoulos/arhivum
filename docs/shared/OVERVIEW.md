# Architecture Overview

## System Context

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         USER'S ENVIRONMENT                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────┐    ┌───────────────┐    ┌───────────────┐          │
│  │ External HDD  │    │ External HDD  │    │ Cloud Services│          │
│  │ (USB3)        │    │ (USB3)        │    │ (OneDrive,    │          │
│  │               │    │               │    │  GDrive, etc) │          │
│  └───────┬───────┘    └───────┬───────┘    └───────┬───────┘          │
│          │                    │                    │                   │
│          └────────────────────┼────────────────────┘                   │
│                               │                                        │
│                               ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                    USER'S COMPUTER                              │  │
│  │                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │              archivum-scanner (CLI)                     │   │  │
│  │  │                                                         │   │  │
│  │  │  • Walks filesystem                                     │   │  │
│  │  │  • Computes SHA-256 hashes                              │   │  │
│  │  │  • Extracts metadata (EXIF, file dates)                 │   │  │
│  │  │  • Sends results to server via REST API                 │   │  │
│  │  │  • Copies files to NAS staging (optional)               │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  │                               │                                 │  │
│  └───────────────────────────────┼─────────────────────────────────┘  │
│                                  │                                     │
└──────────────────────────────────┼─────────────────────────────────────┘
                                   │
                                   │ REST API (HTTPS)
                                   │ (scan results, commands)
                                   │
┌──────────────────────────────────┼─────────────────────────────────────┐
│                          PROXMOX VM                                    │
├──────────────────────────────────┼─────────────────────────────────────┤
│                                  ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │              archivum-server (Spring Boot)                      │  │
│  │                                                                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │  │
│  │  │ REST API    │  │ WebSocket   │  │ Background Jobs         │ │  │
│  │  │ Controllers │  │ (progress)  │  │ (classification, etc)   │ │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────┘ │  │
│  │                                                                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │  │
│  │  │ Scan        │  │ Dedup       │  │ Classification          │ │  │
│  │  │ Service     │  │ Service     │  │ Service                 │ │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────┘ │  │
│  │                                                                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │  │
│  │  │ Migration   │  │ Claude API  │  │ Structure               │ │  │
│  │  │ Service     │  │ Client      │  │ Service                 │ │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────┘ │  │
│  │                           │                                     │  │
│  │  ┌────────────────────────┼────────────────────────────────┐   │  │
│  │  │              Spring Data JPA                            │   │  │
│  │  └────────────────────────┼────────────────────────────────┘   │  │
│  │                           │                                     │  │
│  └───────────────────────────┼─────────────────────────────────────┘  │
│                              │                                        │
│  ┌───────────────────────────┼────────────────────────────────────┐  │
│  │              PostgreSQL Database                               │  │
│  │                                                                 │  │
│  │  • Sources (scanned disks/clouds)                              │  │
│  │  • Files (all discovered files)                                │  │
│  │  • Hashes (deduplication)                                      │  │
│  │  • Classifications                                              │  │
│  │  • Migration jobs                                               │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │              archivum-ui (React + Vite)                         │  │
│  │                                                                 │  │
│  │  • Source browser                                               │  │
│  │  • Zone management                                              │  │
│  │  • Duplicate review                                             │  │
│  │  • Classification review                                         │  │
│  │  • Archive structure editor                                     │  │
│  │  • Rules editor                                                 │  │
│  │  • Progress monitoring                                          │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                        │
└────────────────────────────────┬───────────────────────────────────────┘
                                 │
                                 │ NFS/SMB Mount
                                 │
┌────────────────────────────────┼───────────────────────────────────────┐
│                     SYNOLOGY NAS DS723+                                │
├────────────────────────────────┼───────────────────────────────────────┤
│                                ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  /volume1/Archivum/                                             │  │
│  │    /Staging/              ← Files copied here during scan       │  │
│  │                                                                 │  │
│  │  /volume1/Archive/        ← Final organized location            │  │
│  │    /Life/                                                       │  │
│  │    /Work/                                                       │  │
│  │    /Documents/                                                  │  │
│  │    /Books/                                                      │  │
│  │    /...                                                         │  │
│  │                                                                 │  │
│  │  /volume1/Photos/         ← Synology Photos upload target       │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

## Component Details

### archivum-scanner

**Purpose:** Lightweight CLI that runs where disks are physically connected.

**Technology:** Java 21, minimal dependencies (no Spring)

**Responsibilities:**
1. Walk filesystem and collect file metadata
2. Compute SHA-256 hashes for each file
3. Extract additional metadata (EXIF for photos, etc.)
4. Send results to archivum-server via REST API
5. Optionally copy files to NAS staging area
6. Report progress to user

**Key Design Decisions:**
- No database — stateless, gets state from server
- Minimal memory footprint — streams files, doesn't load into memory
- Resumable — can continue interrupted scans
- Parallel hashing — uses multiple threads for performance

```
archivum-scanner/
├── src/main/java/tech/zaisys/archivum/scanner/
│   ├── ScannerApp.java              # CLI entry point
│   ├── ScanCommand.java             # Scan command implementation
│   ├── CopyCommand.java             # Copy to staging command
│   ├── StatusCommand.java           # Check status command
│   ├── filesystem/
│   │   ├── FileWalker.java          # Walks directory tree
│   │   ├── MetadataExtractor.java   # Extracts file metadata
│   │   └── HashComputer.java        # Computes SHA-256
│   ├── api/
│   │   └── ServerClient.java        # REST client for server
│   └── config/
│       └── ScannerConfig.java       # Configuration (server URL, etc.)
├── build.gradle
└── gradle/wrapper/...
```

### archivum-server

**Purpose:** Central coordination, storage, and intelligence.

**Technology:** Java 21, Spring Boot 3, PostgreSQL

**Responsibilities:**
1. Receive and store scan results
2. Analyze duplicates (file-level and folder-level)
3. Detect software roots and zones
4. Run classification rules
5. Call Claude API for ambiguous files
6. Orchestrate file migration
7. Serve REST API for UI
8. Send real-time updates via WebSocket

**Key Design Decisions:**
- Stateless application (all state in PostgreSQL)
- Background jobs for long-running tasks
- Event-driven architecture for loose coupling
- Idempotent operations for reliability

```
archivum-server/
├── src/main/java/tech/zaisys/archivum/server/
│   ├── ArchivumServerApplication.java
│   ├── api/
│   │   ├── controller/
│   │   │   ├── SourceController.java
│   │   │   ├── FileController.java
│   │   │   ├── DuplicateController.java
│   │   │   ├── ClassificationController.java
│   │   │   ├── MigrationController.java
│   │   │   └── StructureController.java
│   │   ├── dto/
│   │   │   ├── ScanResultDto.java
│   │   │   ├── FileDto.java
│   │   │   ├── DuplicateGroupDto.java
│   │   │   └── ...
│   │   └── websocket/
│   │       └── ProgressWebSocketHandler.java
│   ├── service/
│   │   ├── ScanService.java
│   │   ├── DedupService.java
│   │   ├── ZoneDetectionService.java
│   │   ├── SoftwareRootDetector.java
│   │   ├── ClassificationService.java
│   │   ├── ClaudeApiClient.java
│   │   ├── MigrationService.java
│   │   └── StructureService.java
│   ├── repository/
│   │   ├── SourceRepository.java
│   │   ├── ScannedFileRepository.java
│   │   ├── FileHashRepository.java
│   │   ├── DuplicateGroupRepository.java
│   │   └── ...
│   ├── model/
│   │   ├── Source.java
│   │   ├── ScannedFile.java
│   │   ├── FileHash.java
│   │   ├── DuplicateGroup.java
│   │   ├── Zone.java
│   │   ├── ClassificationRule.java
│   │   └── ...
│   ├── job/
│   │   ├── DedupAnalysisJob.java
│   │   ├── ClassificationJob.java
│   │   └── MigrationJob.java
│   └── config/
│       ├── SecurityConfig.java
│       ├── WebSocketConfig.java
│       └── ClaudeApiConfig.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       ├── V001__initial_schema.sql
│       └── ...
├── build.gradle
└── gradle/wrapper/...
```

### archivum-ui

**Purpose:** Web interface for review and management.

**Technology:** React 18, TypeScript, Vite, shadcn/ui, Tailwind

**Responsibilities:**
1. Browse scanned sources and folder trees
2. Override zone classifications
3. Review and resolve duplicate groups
4. Classify ambiguous files
5. Manage archive folder structure
6. Define classification rules
7. Monitor scan and migration progress

**Key Design Decisions:**
- Component-based architecture
- React Query for server state
- Optimistic updates where safe
- Responsive design (works on tablet)

```
archivum-ui/
├── src/
│   ├── components/
│   │   ├── ui/                      # shadcn components
│   │   │   ├── button.tsx
│   │   │   ├── dialog.tsx
│   │   │   ├── dropdown-menu.tsx
│   │   │   └── ...
│   │   ├── layout/
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   └── Layout.tsx
│   │   ├── sources/
│   │   │   ├── SourceList.tsx
│   │   │   ├── SourceTree.tsx
│   │   │   └── ZoneDropdown.tsx
│   │   ├── duplicates/
│   │   │   ├── DuplicateList.tsx
│   │   │   ├── DuplicateGroup.tsx
│   │   │   └── FileCompare.tsx
│   │   ├── classification/
│   │   │   ├── ReviewQueue.tsx
│   │   │   ├── FileClassifier.tsx
│   │   │   └── RulesEditor.tsx
│   │   ├── structure/
│   │   │   ├── ArchiveTree.tsx
│   │   │   └── FolderEditor.tsx
│   │   └── progress/
│   │       ├── ScanProgress.tsx
│   │       └── MigrationProgress.tsx
│   ├── hooks/
│   │   ├── useSources.ts
│   │   ├── useDuplicates.ts
│   │   ├── useClassification.ts
│   │   └── useWebSocket.ts
│   ├── api/
│   │   ├── client.ts                # Axios instance
│   │   ├── sources.ts
│   │   ├── duplicates.ts
│   │   ├── classification.ts
│   │   └── migration.ts
│   ├── store/
│   │   └── uiStore.ts               # UI state (Zustand)
│   ├── pages/
│   │   ├── SourcesPage.tsx
│   │   ├── DuplicatesPage.tsx
│   │   ├── ClassificationPage.tsx
│   │   ├── StructurePage.tsx
│   │   └── SettingsPage.tsx
│   ├── App.tsx
│   └── main.tsx
├── package.json
├── tailwind.config.js
├── vite.config.ts
└── tsconfig.json
```

## Data Flow

### Scanning Flow

```
1. User plugs in disk
2. User runs: archivum scan /media/disk1 --name "Backup-2018"
3. Scanner walks filesystem
4. For each file:
   a. Compute SHA-256 hash
   b. Extract metadata
   c. Batch results (every 1000 files)
   d. POST to server: /api/sources/{id}/files
5. Server stores in database
6. Server triggers zone detection
7. Server triggers dedup analysis
8. User sees results in UI
```

### Deduplication Flow

```
1. After scan completes, server runs dedup analysis
2. For file-level duplicates:
   a. Group files by hash
   b. Filter by zone (skip SOFTWARE zones)
   c. Create DuplicateGroup records
3. For folder-level duplicates:
   a. Compute folder content hashes
   b. Compare folders, compute similarity
   c. Create FolderDuplicateGroup records
4. User reviews in UI
5. User selects which copies to keep
6. Server marks others as duplicates (not deleted yet)
```

### Classification Flow

```
1. For each unclassified file:
   a. Apply rule engine (user-defined rules)
   b. If confident match: classify automatically
   c. If uncertain: queue for review
2. For files needing AI:
   a. Extract context (filename, path, metadata)
   b. Call Claude API
   c. Apply suggestion or queue for review
3. User reviews uncertain files in UI
4. User decisions update rules (learning)
```

### Migration Flow

```
1. User reviews files in UI
2. User approves migration batch
3. Server creates MigrationJob
4. For each file:
   a. Compute target path based on classification
   b. Copy file from staging to /Archive
   c. Verify hash matches
   d. Update database with new location
   e. Mark original as migrated
5. Progress reported via WebSocket
```

## External Integrations

### Claude API

Used for:
- Book classification by subject
- Document type detection
- Ambiguous photo classification (daily vs event vs reference)

Configuration:
```yaml
claude:
  api-key: ${CLAUDE_API_KEY}
  model: claude-sonnet-4-20250514
  max-tokens: 1000
```

### Synology Photos Integration

The toolkit watches `/volume1/Photos/PhotoLibrary/` for new uploads:
1. Synology Photos receives upload from mobile
2. archivum-watcher detects new files
3. Files are classified and moved to `/Archive/Life/Daily/`
4. Original location cleared (or symlinked)

## Security Considerations

1. **API Authentication:** Scanner uses API key to authenticate with server
2. **Network:** Server should only be accessible from local network
3. **File Access:** Server needs NFS/SMB access to NAS for migration
4. **Claude API Key:** Stored in environment variable, not in code
5. **No Public Exposure:** This is a home/personal tool, not for public internet

## Deployment

See `/docs/architecture/DEPLOYMENT.md` for detailed deployment instructions.

Quick overview:
```bash
# Docker Compose deploys everything
docker-compose up -d

# Scanner runs separately on user's computer
java -jar archivum-scanner.jar scan /media/disk1 --name "Backup-2018"
```
