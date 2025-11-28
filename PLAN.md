# Archivum Development Plan

**Last Updated:** 2025-11-28
**Current Phase:** Scanner MVP
**Active Milestone:** Milestone 3 - Advanced Metadata (ready to start)

---

## Status Overview

✅ Completed: 3 milestones
🔄 In Progress: None (ready to start Milestone 3)
📋 Upcoming: 3 milestones (implementation)
⏳ Future: Server, UI, advanced features

---

## Completed Milestones

### ✅ Milestone 0: Shared API Module (Nov 27, 2025)

**Duration:** 3 hours
**PR:** [#2](https://github.com/hmichopoulos/arhivum/pull/2) - Merged

**Deliverables:**
- Created `archivum-api` module with Java library configuration
- Multi-module Gradle build (`archivum-api`, `archivum-scanner`, `archivum-server`)
- Defined all shared DTOs and enums:
  - `SourceDto`, `FileDto`, `PhysicalId`, `ExifMetadata`, `GpsCoordinates`
  - `FileBatchDto`, `CreateSourceRequest`, `CompleteScanRequest`, `SourceResponse`
  - `CheckHashRequest`, `CheckHashResponse`
  - Enums: `SourceType`, `ScanStatus`, `FileStatus`
- Updated scanner and server build files to depend on `archivum-api`
- Verified build: `./gradlew build` ✅

**Key Files:**
- `archivum-api/build.gradle`
- `archivum-api/src/main/java/tech/zaisys/archivum/api/`
- `settings.gradle` (root)

---

### ✅ Milestone 1: Documentation Foundation (Nov 28, 2025)

**Duration:** 6 hours
**PR:** [#3](https://github.com/hmichopoulos/arhivum/pull/3) - Merged

**Deliverables:**
- Reorganized docs/ structure into `scanner/`, `server/`, `shared/`
- Created README.md in each docs subfolder
- **docs/scanner/REQUIREMENTS.md** (3,500 words, 12 functional requirements)
- **docs/scanner/DESIGN.md** (4,000 words, 11 core components)
- **docs/scanner/SCANNER_AGENT.md** (7,000 words, complete implementation spec)
- **docs/server/API.md** (3,000 words, REST endpoints, WebSocket)
- **PLAN.md** - High-level roadmap with milestone tracking
- **CLAUDE.md** - Updated with development workflow section

**Key Files:**
- `PLAN.md`
- `CLAUDE.md` (workflow section)
- `docs/scanner/` (4 new docs)
- `docs/server/` (2 new docs)
- `docs/shared/` (4 docs, 3 moved)

**Acceptance Criteria:**
- ✅ All documentation is comprehensive and clear
- ✅ PLAN.md accurately tracks current status
- ✅ CLAUDE.md includes workflow section
- ✅ PR submitted and merged

---

### ✅ Milestone 2: Foundation & Core Scanning (Nov 28, 2025)

**Duration:** 10 hours
**PR:** [#4](https://github.com/hmichopoulos/arhivum/pull/4) - Submitted

**Deliverables:**
- Created core scanning services:
  - **ConfigLoader** - YAML config loading with env var overrides
  - **ScannerConfig** - Configuration POJO with nested classes
  - **HashService** - Parallel SHA-256 computation (streaming, thread pool)
  - **FileWalkerService** - Recursive directory traversal with exclusions
  - **MetadataService** - Basic file metadata extraction (name, size, dates, MIME)
  - **OutputService** - JSON batch writer for dry-run mode
  - **ProgressReporter** - Real-time console progress with ANSI codes
  - **ScanCommand** - Main CLI orchestration command

- Comprehensive test coverage (48 tests):
  - HashServiceTest (9 tests)
  - MetadataServiceTest (11 tests)
  - OutputServiceTest (10 tests)
  - FileWalkerServiceTest (14 tests)
  - ScanIntegrationTest (2 tests, end-to-end workflows)

**Key Files:**
- `archivum-scanner/src/main/java/tech/zaisys/archivum/scanner/command/ScanCommand.java`
- `archivum-scanner/src/main/java/tech/zaisys/archivum/scanner/service/` (7 services)
- `archivum-scanner/src/test/` (5 test files)

**Acceptance Criteria:**
- ✅ Computes correct SHA-256 hashes (verified with test vectors)
- ✅ Outputs batched JSON files (batch-0001.json, etc.)
- ✅ Excludes system directories (`.Trash`, `$RECYCLE.BIN`, etc.)
- ✅ Shows real-time progress in console
- ✅ Unit tests pass with 48 tests total
- ✅ Build produces fat JAR artifact (`./gradlew build` ✅)
- ⏳ Manual test with 10K files (pending user testing)

---

## Current Milestone

### 📋 Milestone 3: Advanced Metadata (Next)

**Duration:** ~8 hours
**Status:** Ready to start
**Planned Start:** After Milestone 2 merged

**Scope:**
Add EXIF extraction, physical device ID detection, interactive prompts, and hash-based optimization.

**Components to Build:**
1. **ExifExtractor** - Extract EXIF metadata from JPEG/PNG/TIFF
2. **PhysicalIdDetector** - Detect disk/partition UUIDs, serial numbers, volume labels
3. **InteractivePrompt** - Interactive user prompts with smart defaults
4. **Enhanced OutputService** - Hash cache for duplicate optimization
5. **Unit tests** - Test EXIF extraction, physical ID detection

**Key Features:**
- Extract camera make/model, GPS coordinates, capture dates
- Auto-detect disk UUID, partition UUID, serial number
- Prompt for physical label (sticker on disk) and notes
- Skip EXIF extraction for files with duplicate hashes (optimization)

**Acceptance Criteria:**
- [ ] Extracts EXIF (camera, GPS, dates) from JPEG/PNG images
- [ ] Detects disk UUID, partition UUID, serial number (Linux/macOS)
- [ ] Prompts user for physical label and notes with smart defaults
- [ ] Skips EXIF extraction for duplicate hashes (verified in tests)
- [ ] Tests pass with >80% coverage
- [ ] Manual test with 1,000 photos completes in < 2 minutes

**Estimated Timeline:** 1 day of focused work

---

## Upcoming Milestones

### 📋 Milestone 4: Hierarchy & Archives

**Duration:** ~10 hours
**Status:** Not started
**Planned Start:** After Milestone 3 merged

**Scope:**
Implement source hierarchy management, archive detection, postponement workflow, and open-archive command.

**Components to Build:**
1. **SourceHierarchyService** - Manage parent-child source relationships
2. **ArchiveDetector** - Identify archives by extension and magic bytes
3. **Enhanced ScanCommand** - Interactive prompts for archive postponement
4. **OpenArchiveCommand** - Extract and scan postponed archives
5. **Unit tests** - Test hierarchy creation, archive detection

**Key Features:**
- Create hierarchies: Disk → Partition → Archive
- Detect archives (.tar, .zip, .7z, .tib, .img, etc.)
- Prompt user to postpone large archives (>100MB)
- Record postponed archives as sources with `postponed=true`
- Detect duplicate archives by hash across multiple disks

**Acceptance Criteria:**
- [ ] Creates source hierarchies (Disk → Partition → Archive)
- [ ] Detects archives by extension (.tar, .zip, .7z, .tib, etc.)
- [ ] Prompts to postpone large archives with smart defaults
- [ ] Records postponed archives as sources in JSON
- [ ] `open-archive` command extracts and scans postponed archives
- [ ] Detects duplicate archives by hash (skip re-scanning)
- [ ] Tests pass with >80% coverage

**Estimated Timeline:** 1-2 days of focused work

---

### 📋 Milestone 5: Copy & Polish

**Duration:** ~8 hours
**Status:** Not started
**Planned Start:** After Milestone 4 merged

**Scope:**
Add copy-to-staging functionality, enhanced progress reporting, status commands, error handling polish, and integration tests.

**Components to Build:**
1. **CopyService** - Copy files to staging with hash verification
2. **CopyCommand** - Standalone copy command
3. **StatusCommand** - Show scan status with hierarchy view
4. **Enhanced ProgressReporter** - Better formatting and ETA calculation
5. **Error handling** - Graceful degradation for common errors
6. **Integration tests** - End-to-end scenarios

**Key Features:**
- Copy files to staging directory with parallel threads
- Verify hash after copy to ensure integrity
- Show copy progress separately from scan progress
- Display source hierarchy in status output
- Handle common errors gracefully (permission denied, disk full, etc.)

**Acceptance Criteria:**
- [ ] Copies files to staging directory preserving structure
- [ ] Verifies hash after copy (reports mismatches)
- [ ] Shows copy progress with speed and ETA
- [ ] `status` command shows source hierarchy tree
- [ ] Handles common errors gracefully (logs, continues)
- [ ] Integration tests pass (end-to-end scenarios)
- [ ] Fat JAR works standalone (tested on clean system)
- [ ] Manual test: copy 10,000 files, verify all hashes match

**Estimated Timeline:** 1 day of focused work

---

## Scanner MVP Complete

### Definition of Done

Scanner MVP is considered complete when all 5 milestones are done and the following criteria are met:

#### Functional Completeness
- ✅ Walk directory trees recursively
- ✅ Compute SHA-256 hashes (streaming, parallel)
- ✅ Extract basic file metadata (name, size, dates, permissions)
- ✅ Extract EXIF metadata from images (camera, GPS, dates)
- ✅ Detect physical device identifiers (UUIDs, serial numbers)
- ✅ Interactive prompts for user input (name, physical label, notes)
- ✅ Source hierarchy management (Disk → Partition → Archive)
- ✅ Archive detection and postponement workflow
- ✅ Copy files to staging directory with verification
- ✅ Dry-run mode (save to local JSON files)
- ✅ Configuration (YAML file + environment variables + CLI overrides)
- ✅ Progress reporting (console output with stats)
- ✅ Error handling (graceful degradation)
- ✅ Exclude patterns for unwanted files

#### Quality & Performance
- ✅ Unit test coverage >80%
- ✅ Integration tests pass (end-to-end scenarios)
- ✅ Performance targets met:
  - 10,000 files scanned in < 5 minutes (HDD)
  - Memory usage < 500 MB
  - Hash speed: 100+ MB/sec (HDD), 500+ MB/sec (SSD)
- ✅ Fat JAR builds successfully
- ✅ Manual testing completed on real datasets

#### Documentation
- ✅ REQUIREMENTS.md (comprehensive)
- ✅ DESIGN.md (architecture and design decisions)
- ✅ SCANNER_AGENT.md (complete implementation spec)
- ✅ README.md (usage instructions)
- ✅ Code comments (where logic is not self-evident)

### Explicitly Out of Scope (Future Work)

The following features are **not included in MVP** and will be implemented later:

❌ **Server API communication** (dry-run only for MVP)
❌ **Resume functionality** (scan state persistence)
❌ **Advanced error recovery** (exponential backoff, retry logic)
❌ **Video/audio metadata extraction** (focus on images for MVP)
❌ **Document metadata extraction** (PDF, Office files)
❌ **Cloud source scanning** (Google Drive, OneDrive, Dropbox)
❌ **SSH/SFTP remote scanning** (local disks only)
❌ **Incremental scanning** (only scan new/modified files)
❌ **GUI wrapper** (CLI only for MVP)

---

## Timeline Summary

| Milestone | Duration | Status | Started | Completed |
|-----------|----------|--------|---------|-----------|
| M0: Shared API | 3h | ✅ Complete | Nov 27 | Nov 27 |
| M1: Documentation | 6h | ✅ Complete | Nov 28 | Nov 28 |
| M2: Foundation & Core | 10h | ✅ Complete | Nov 28 | Nov 28 |
| M3: Advanced Metadata | 8h | 📋 Next | TBD | TBD |
| M4: Hierarchy & Archives | 10h | 📋 Upcoming | TBD | TBD |
| M5: Copy & Polish | 8h | 📋 Upcoming | TBD | TBD |
| **Total** | **45h** | **42% done** | | |

**Estimated Completion:** 3-4 weeks (assuming ~8-10h/week)

---

## Future Work (Post-MVP)

### Phase 2: Server Implementation

**Scope:** Build archivum-server backend with REST API, database, and background jobs.

**Key Components:**
- REST API endpoints (as documented in docs/server/API.md)
- PostgreSQL schema (sources, files, hashes, duplicates)
- Deduplication service (file-level and folder-level)
- Zone detection service (MEDIA, DOCUMENTS, SOFTWARE, etc.)
- Classification service (with Claude API integration)
- Migration service (copy to final archive structure)
- WebSocket for real-time progress updates

**Duration:** ~60-80 hours

---

### Phase 3: UI Development

**Scope:** Build archivum-ui React application for review and management.

**Key Features:**
- Source browser (list, filter, search)
- File browser (hierarchical tree view)
- Duplicate review UI (accept/reject duplicates)
- Classification review UI (confirm/override classifications)
- Archive structure editor (manage target folder structure)
- Rules editor (define classification rules)
- Progress monitoring (real-time scan/migration status)

**Duration:** ~40-60 hours

---

### Phase 4: Integration & Advanced Features

**Scope:** Connect scanner to server, add advanced features.

**Key Features:**
- Replace `JsonOutput` with `ApiClientService` in scanner
- Resume functionality (scan state persistence)
- Incremental scanning (only scan new/modified files)
- Cloud source scanning (Google Drive, OneDrive, Dropbox)
- SSH/SFTP remote scanning
- Video/audio metadata extraction
- Document metadata extraction (PDF, Office)
- Advanced error recovery with exponential backoff

**Duration:** ~30-40 hours

---

## Development Workflow

### Work Loop

1. **Check PLAN.md** - Review current milestone and next tasks
2. **Communicate** - I describe what I'll do in this PR (scope, changes, criteria)
3. **Create branch** - `git checkout -b feature/milestone-X-name`
4. **Implement** - Write code, tests, update PLAN.md progress
5. **Create PR** - Clear description, link to PLAN.md, tests passing
6. **Review & Merge** - You review, I address feedback, you merge
7. **Pull main** - `git checkout main && git pull`
8. **Update PLAN.md** - Mark milestone complete, update status
9. **Loop** - Return to step 1

### Branch Naming

- `feature/milestone-X-name` - Feature implementation
- `docs/topic` - Documentation updates
- `fix/issue-description` - Bug fixes

### PR Requirements

Each PR must include:
- Clear scope and acceptance criteria
- Tests (unit tests for new code)
- Updated PLAN.md (progress, status)
- Passing build (`./gradlew build`)

### Retros

After every 2-3 milestones, we do a quick retrospective:
- What went well?
- What could improve?
- Adjust workflow if needed

---

## Notes

- Each milestone = 1 focused PR (~8-10h of work)
- All tests must pass before PR submission
- PLAN.md updated with each milestone completion
- Focus on quality over speed (iterative but thorough)
- This is a personal project, not production software (but we aim for high quality)

---

**Maintained by:** Haris + Claude
**Project Start:** November 2025
**Last Review:** November 28, 2025
