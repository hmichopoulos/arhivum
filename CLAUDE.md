# CLAUDE.md - Instructions for Claude Code

## Project Overview

**Archivum** is a personal file organization toolkit that helps organize tens of terabytes of files scattered across multiple external disks and cloud services into a clean, structured archive.

The user has ~20 external HDDs (mostly 4TB each), multiple cloud accounts (OneDrive, Google Drive, Dropbox, Apple, SSH servers), and wants to consolidate everything onto a Synology NAS with a well-defined folder structure.

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────┐
│               USER'S COMPUTER (Laptop/Desktop)              │
│                                                             │
│  archivum-scanner (CLI)                                     │
│  - Scans plugged-in disks                                   │
│  - Computes SHA-256 hashes locally                          │
│  - Sends metadata to archivum-server via REST API           │
│  - Copies files to NAS staging area                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ REST API
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     PROXMOX VM                              │
│                                                             │
│  archivum-server (Spring Boot)                              │
│  - PostgreSQL database (catalog)                            │
│  - Deduplication analysis                                   │
│  - AI classification (Claude API)                           │
│  - Migration orchestration                                  │
│                                                             │
│  archivum-ui (React)                                        │
│  - Web interface for review and management                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ NFS/SMB mount
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   SYNOLOGY NAS                              │
│                                                             │
│  /volume1/Archivum/Staging/    ← Files copied here first   │
│  /volume1/Archive/             ← Final organized location  │
└─────────────────────────────────────────────────────────────┘
```

## Tech Stack

### Backend (archivum-server)
- Java 21
- Gradle 8.x (build tool)
- Spring Boot 3
- Spring Web (REST controllers)
- Spring WebSocket (real-time updates)
- Spring Data JPA
- PostgreSQL 16
- Flyway (database migrations)

### Frontend (archivum-ui)
- React 18
- TypeScript
- Vite (build tool)
- shadcn/ui (component library)
- Tailwind CSS
- Tanstack Table (data tables)
- Tanstack Query (data fetching)
- React Router

### Scanner (archivum-scanner)
- Java 21
- Gradle 8.x (build tool)
- Lightweight CLI application
- No Spring dependencies (keep it minimal)
- Communicates with server via REST

## Project Structure

```
archivum/
├── archivum-server/           # Spring Boot backend
├── archivum-scanner/          # CLI scanner tool
├── archivum-ui/               # React frontend
├── docs/                      # Documentation
└── docker-compose.yml         # Deployment
```

## Coding Conventions

### General
- Update the docs with the changes when done


### Java (Backend + Scanner)

1. **Keep methods short** — ideally < 30 lines, hard limit 40 lines
2. **Single responsibility** — each class does one thing well
3. **Design for testability** — use dependency injection, avoid static methods
4. **Prefer maintainability over brevity** — clear code over clever code
5. **Use established libraries** — don't reinvent the wheel
6. **Reduce boilerplate code** - use libraries like lombok or Immutable
7. **Promote stateless code** - whenever possible, avoid keeping the state in classes. methods params should be enough
8. **Promote immutability** - don't use mutable classes for collections, unless there are very good reasons.
9. **Use names that reflect the functionality** - the names and the short methods, should be enough to understand, comments must be minimal
10. **Database** - always add an index on foreign keys, always use a surrogate primary key
11. **Write tests** - write unit tests for almost everything, use testcontainers for integration tests to test the important or risky flows

#### Naming
- Classes: `PascalCase`
- Methods/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: `lowercase`

#### Structure
```java
// Order within a class:
// 1. Static fields
// 2. Instance fields
// 3. Constructors
// 4. Public methods
// 5. Private methods
```

#### Example Style
```java
@Service
@RequiredArgsConstructor
public class FileHashService {
    
    private final HashAlgorithm hashAlgorithm;
    
    public String computeHash(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return hashAlgorithm.hash(is);
        }
    }
}
```

### TypeScript (Frontend)

1. **Functional components** — no class components
2. **Named exports** — avoid default exports
3. **Types over interfaces** for simple types
4. **Colocate related code** — component + styles + tests together

#### Naming
- Components: `PascalCase`
- Functions/variables: `camelCase`
- Types: `PascalCase`
- Files: `kebab-case.tsx` for components, `camelCase.ts` for utilities

#### Example Style
```tsx
type SourceBrowserProps = {
  sourceId: string;
  onSelect: (path: string) => void;
};

export function SourceBrowser({ sourceId, onSelect }: SourceBrowserProps) {
  const { data, isLoading } = useSource(sourceId);
  
  if (isLoading) return <Skeleton />;
  
  return (
    <div className="flex flex-col gap-2">
      {data.folders.map(folder => (
        <FolderItem 
          key={folder.path} 
          folder={folder} 
          onClick={() => onSelect(folder.path)} 
        />
      ))}
    </div>
  );
}
```

## Key Concepts

### Zones

Files are categorized into zones that determine deduplication behavior:

| Zone | File Dedup | Folder Dedup | Description |
|------|------------|--------------|-------------|
| MEDIA | Yes | Yes | Photos, videos, music |
| DOCUMENTS | Yes | Yes | PDFs, Office files |
| BOOKS | Yes | Yes | Ebooks |
| SOFTWARE | No | Yes | Installers, apps (preserve DLLs) |
| BACKUP | No | Yes | Full backup sets |
| CODE | No | Yes | Source code repos |
| UNKNOWN | No | No | Needs manual classification |

### Software Roots

For SOFTWARE zone, the system detects "root" folders that represent a complete software unit:

Root markers: `Setup.exe`, `Install.exe`, `*.msi`, `*.app`, `package.json`, `pom.xml`, `.git`

Everything under a root is treated as one atomic unit — never deduplicated at file level.

### File States

```
DISCOVERED → HASHED → ANALYZED → CLASSIFIED → STAGED → MIGRATED
                         ↓
                    DUPLICATE (links to kept file)
```

### Target Folder Structure

See `/docs/requirements/FOLDER_STRUCTURE.md` for the complete taxonomy.

Key top-level folders:
- `/Archive/Life/` — Personal photos, events
- `/Archive/Work/` — Professional projects
- `/Archive/Documents/` — Administrative files (per person + family)
- `/Archive/Books/` — Ebook library
- `/Archive/Media/` — Movies, TV, music
- `/Archive/Software/` — Installers, games
- `/Archive/Reference/` — Functional photos (equipment, receipts)
- `/Archive/Backups/` — Raw device backups

## API Design

### REST Endpoints

```
# Sources
GET    /api/sources                    # List all scanned sources
POST   /api/sources                    # Register new source (from scanner)
GET    /api/sources/{id}               # Get source details
GET    /api/sources/{id}/tree          # Get folder tree
PATCH  /api/sources/{id}/folders/{path}  # Update folder zone

# Files
GET    /api/files                      # Query files (with filters)
GET    /api/files/{id}                 # Get file details
PATCH  /api/files/{id}                 # Update classification

# Duplicates
GET    /api/duplicates                 # Get duplicate groups
POST   /api/duplicates/{groupId}/resolve  # Resolve a duplicate group

# Classification
GET    /api/classification/pending     # Get files needing review
POST   /api/classification/rules       # Create classification rule
POST   /api/classification/apply       # Apply classification to files

# Migration
POST   /api/migration/stage            # Stage files for migration
POST   /api/migration/execute          # Execute migration
GET    /api/migration/status           # Get migration status

# Structure
GET    /api/structure                  # Get archive folder structure
POST   /api/structure/folders          # Create new folder
PATCH  /api/structure/folders/{path}   # Rename/move folder
```

### WebSocket Events

```
/ws/scan-progress     # Scan progress updates
/ws/migration-progress # Migration progress updates
```

## Database Schema

See `/docs/architecture/DATABASE.md` for full schema.

Key entities:
- `Source` — A scanned disk or cloud account
- `ScannedFile` — A file found during scanning
- `FileHash` — SHA-256 hash (shared by duplicates)
- `DuplicateGroup` — Group of files with same hash
- `ClassificationRule` — User-defined classification rules
- `ArchiveFolder` — Target folder structure
- `MigrationJob` — Tracks file migration

## Testing

### Backend
```bash
cd archivum-server
./gradlew test
```

### Frontend
```bash
cd archivum-ui
npm test
```

## Running Locally

### Prerequisites
- Java 21
- Node.js 20+
- PostgreSQL 16
- Docker (optional)

### Development

```bash
# Start PostgreSQL
docker run -d --name archivum-db \
  -e POSTGRES_DB=archivum \
  -e POSTGRES_USER=archivum \
  -e POSTGRES_PASSWORD=archivum \
  -p 5432:5432 \
  postgres:16

# Start backend
cd archivum-server
./gradlew bootRun

# Start frontend
cd archivum-ui
npm install
npm run dev
```

## Common Tasks

### Adding a new API endpoint

1. Create DTO in `archivum-server/src/main/java/tech/zaisys/archivum/server/api/dto/`
2. Create controller method in appropriate controller
3. Create service method if needed
4. Add frontend API call in `archivum-ui/src/api/`
5. Create/update React Query hook in `archivum-ui/src/hooks/`

### Adding a new UI component

1. Check if shadcn/ui has it: `npx shadcn-ui@latest add <component>`
2. If custom, create in `archivum-ui/src/components/`
3. Colocate styles and tests

### Adding a database migration

1. Create new file: `archivum-server/src/main/resources/db/migration/V{next}__{description}.sql`
2. Flyway runs migrations automatically on startup

## Things to Avoid

1. **Don't delete files without user confirmation** — Always require explicit approval
2. **Don't break software units** — Never deduplicate individual DLLs inside SOFTWARE zones
3. **Don't lose provenance** — Always track where files came from
4. **Don't assume filesystem access** — Scanner sends metadata; server may not see disks
5. **Don't block the UI** — Use WebSocket for long operations, show progress

## Documentation Structure

```
docs/
├── README.md                    # Documentation index
├── PROJECT.md                   # High-level overview
├── scanner/                     # Scanner-specific docs
│   ├── README.md
│   ├── REQUIREMENTS.md          # Scanner functional requirements
│   ├── DESIGN.md                # Scanner architecture
│   └── SCANNER_AGENT.md         # Detailed scanner specification
├── server/                      # Server-specific docs
│   ├── README.md
│   ├── DATABASE.md              # Schema design
│   └── API.md                   # REST and WebSocket API
└── shared/                      # Shared concepts
    ├── README.md
    ├── OVERVIEW.md              # System architecture
    ├── FOLDER_STRUCTURE.md      # Target taxonomy
    └── DEDUPLICATION.md         # Dedup logic
```

## Development Workflow

### Planning & Tracking

We use a **milestone-based workflow** with persistent progress tracking in **PLAN.md** (repo root).

**PLAN.md contains:**
- Current milestone with progress percentage
- Completed milestones with links to PRs
- Upcoming milestones with estimates
- Future work roadmap

**Before starting work:**
1. Check PLAN.md for current milestone
2. I communicate what I'm going to do in this PR (scope, changes, acceptance criteria)
3. Create feature branch
4. Implement, test, update PLAN.md progress
5. Create PR
6. You review & merge
7. Pull main, update PLAN.md status
8. Loop back to step 1

### Branch Naming Conventions

- `feature/milestone-X-name` - Feature implementation
  - Example: `feature/milestone-2-core-scanning`
- `docs/topic` - Documentation updates
  - Example: `docs/scanner-requirements`
- `fix/issue-description` - Bug fixes
  - Example: `fix/hash-computation-error`

### PR Requirements

Each PR must include:
- **Clear scope** - What's included, what's not
- **Tests** - Unit tests for new code (>80% coverage target)
- **Updated PLAN.md** - Current milestone progress
- **Passing build** - All tests must pass: `./gradlew build`
- **Clear description** - Link to PLAN.md section

### Milestone Structure

Each milestone is:
- **~8-10 hours of work** - Manageable in 1-2 days of focused work
- **Focused scope** - Related functionality grouped together
- **Clear acceptance criteria** - Specific, testable outcomes
- **One PR** - May include multiple commits, but one cohesive PR

**Example Milestones:**
- Milestone 1: Documentation Foundation (6h)
- Milestone 2: Foundation & Core Scanning (10h)
- Milestone 3: Advanced Metadata (8h)

### Retrospectives

After every 2-3 milestones, we do a quick retrospective:
- **What went well?** - Celebrate successes
- **What could improve?** - Identify friction points
- **Adjust workflow if needed** - Adapt based on what we learn

### Work Communication

**At the start of each PR:**
I communicate clearly what I'm going to do:
- **Scope:** What features/components I'll build
- **Acceptance Criteria:** How we'll know it's done
- **Estimated Time:** How long I expect it to take
- **Dependencies:** What needs to be done first (if any)

**During work:**
- Update PLAN.md progress as I complete tasks
- Communicate if I encounter unexpected complexity
- Ask questions if requirements are unclear

**At PR creation:**
- Clear description of changes
- Link to PLAN.md section
- Highlight any deviations from plan
- Note any follow-up work needed

### Quality Standards

Before submitting PR:
- ✅ All tests pass (`./gradlew build`)
- ✅ Code follows conventions (see above)
- ✅ No compiler warnings
- ✅ Test coverage >80% for new code
- ✅ PLAN.md updated with progress
- ✅ Commit messages are clear and descriptive

### Merge & Continue

After PR is merged:
1. Pull main: `git checkout main && git pull`
2. Update PLAN.md to mark milestone complete
3. Check PLAN.md for next milestone
4. Start new cycle

## Getting Help

- Check existing documentation in `/docs/`
- Check **PLAN.md** for current priorities and context
- Review similar code in the codebase
- Ask the user for clarification on requirements
