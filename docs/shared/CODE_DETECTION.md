# Code Project Detection

## Overview

Code projects (source code repositories) require special handling during archiving:

1. **No file-level deduplication** - Shared files (node_modules, build artifacts, common libraries) shouldn't be deduplicated within a project
2. **Project identity** - Extract project name/version from build files to properly identify what the code is
3. **Smart duplicate detection** - Two copies of the same project should be recognized, but different versions/branches need careful handling
4. **Git integration** (future) - Eventually push to git server instead of just archiving

## Project Types Supported

### Maven (Java)
- **Marker:** `pom.xml`
- **Identity:**
  ```xml
  <groupId>com.example</groupId>
  <artifactId>my-project</artifactId>
  <version>1.0.0</version>
  ```
  Identity: `com.example:my-project:1.0.0`

### Gradle (Java/Kotlin)
- **Markers:** `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`
- **Identity:** Extract from build file
  ```groovy
  group = 'com.example'
  version = '1.0.0'
  // In settings.gradle:
  rootProject.name = 'my-project'
  ```
  Identity: `com.example:my-project:1.0.0`

### NPM (Node.js/JavaScript)
- **Marker:** `package.json`
- **Identity:**
  ```json
  {
    "name": "@scope/my-package",
    "version": "2.3.1"
  }
  ```
  Identity: `@scope/my-package:2.3.1`

### Go
- **Marker:** `go.mod`
- **Identity:**
  ```go
  module github.com/user/myproject

  go 1.21
  ```
  Identity: `github.com/user/myproject` (version from git tags)

### Python
- **Markers:** `setup.py`, `pyproject.toml`, `requirements.txt`
- **Identity from pyproject.toml:**
  ```toml
  [project]
  name = "my-package"
  version = "0.1.0"
  ```
  Identity: `my-package:0.1.0`

- **Identity from setup.py:**
  ```python
  setup(
      name='my-package',
      version='0.1.0',
  )
  ```

### Rust
- **Marker:** `Cargo.toml`
- **Identity:**
  ```toml
  [package]
  name = "my-crate"
  version = "0.1.0"
  ```
  Identity: `my-crate:0.1.0`

### Git Repository
- **Marker:** `.git/` directory
- **Identity:** Extract from git remote URL
  ```bash
  git config --get remote.origin.url
  # → https://github.com/user/repo.git
  # → git@github.com:user/repo.git
  ```
  Identity: `github.com/user/repo` + current branch/commit

### Generic/Unknown
- **Markers:** Common code patterns
  - `src/` directory
  - Multiple source files (.java, .js, .py, .go, etc.)
  - `.gitignore` file
- **Identity:** Use folder name
  Identity: `unknown:foldername`

## Project Detection Algorithm

```
For each folder during scan:
  1. Check for project markers (pom.xml, package.json, go.mod, etc.)
  2. If marker found:
     a. Identify project type
     b. Extract metadata (name, version, etc.)
     c. Create ProjectIdentity
     d. Mark folder as CODE zone
     e. Mark as "codebase root" (don't deduplicate files within)
  3. If no marker found but looks like code:
     a. Mark as CODE zone
     b. Create generic ProjectIdentity using folder name
  4. Continue scanning subdirectories (handle nested projects)
```

## Project Identity

### Structure

```java
public class ProjectIdentity {
    private ProjectType type;        // MAVEN, NPM, GO, etc.
    private String name;              // Project name
    private String version;           // Version (if available)
    private String groupId;           // For Maven/Gradle
    private String gitRemote;         // For Git repos
    private String gitBranch;         // For Git repos
    private String gitCommit;         // For Git repos (short SHA)

    // Computed identifier
    public String getIdentifier() {
        return switch (type) {
            case MAVEN, GRADLE -> groupId + ":" + name + ":" + version;
            case NPM -> name + ":" + version;
            case GO -> name; // Module path is the name
            case PYTHON, RUST -> name + ":" + version;
            case GIT -> gitRemote + "@" + gitBranch;
            case GENERIC -> "unknown:" + name;
        };
    }
}
```

### Examples

| Type | Identifier |
|------|------------|
| Maven | `com.example:my-api:1.0.0` |
| Gradle | `com.example:my-api:1.0.0` |
| NPM | `@myorg/my-package:2.3.1` |
| Go | `github.com/user/myproject` |
| Python | `my-package:0.1.0` |
| Rust | `my-crate:0.1.0` |
| Git | `github.com/user/repo@main` |
| Generic | `unknown:my-scripts` |

## Smart Duplicate Detection

### Exact Match (100% identical)

Two projects are exactly identical if:
1. Same project identifier
2. Same content hash (hash of all file hashes)

**Action:** Mark as duplicate, keep one copy

```
Example:
  /Disk1/Projects/my-api/     (com.example:my-api:1.0.0, hash: abc123)
  /Disk3/Backup/my-api/       (com.example:my-api:1.0.0, hash: abc123)

  → Same project, same content → DUPLICATE
```

### Same Project, Different Content

Two projects are the same project but different if:
1. Same project identifier (or similar name but different version)
2. Different content hash

**Action:** Flag for manual review, calculate similarity

```
Example 1: Same version, different content (likely different branches or uncommitted changes)
  /Disk1/Projects/my-api/     (com.example:my-api:1.0.0, hash: abc123)
  /Disk2/Projects/my-api/     (com.example:my-api:1.0.0, hash: def456)

  → Same identifier, different content → REVIEW (similarity: 87%)

Example 2: Different versions
  /Disk1/Projects/my-api/     (com.example:my-api:1.0.0)
  /Disk2/Projects/my-api/     (com.example:my-api:2.0.0)

  → Same project, different version → REVIEW (similarity: 45%)
```

### Different Projects

Different project identifiers → Keep both

```
Example:
  /Disk1/Projects/my-api/     (com.example:my-api:1.0.0)
  /Disk2/Projects/other-api/  (com.example:other-api:1.0.0)

  → Different projects → KEEP BOTH
```

### Similarity Calculation for Code

For projects flagged for manual review, calculate how different they are:

```java
public CodeProjectSimilarity calculateSimilarity(CodeProject a, CodeProject b) {
    // Get all source files (exclude build artifacts, dependencies)
    Set<String> sourceHashesA = getSourceFileHashes(a);
    Set<String> sourceHashesB = getSourceFileHashes(b);

    Set<String> intersection = new HashSet<>(sourceHashesA);
    intersection.retainAll(sourceHashesB);

    Set<String> union = new HashSet<>(sourceHashesA);
    union.addAll(sourceHashesB);

    double jaccardSimilarity = (double) intersection.size() / union.size() * 100;

    // Count files
    int filesOnlyInA = sourceHashesA.size() - intersection.size();
    int filesOnlyInB = sourceHashesB.size() - intersection.size();
    int filesInBoth = intersection.size();

    return new CodeProjectSimilarity(
        jaccardSimilarity,
        filesOnlyInA,
        filesOnlyInB,
        filesInBoth,
        estimateDiffComplexity(a, b) // Simple/Medium/Complex
    );
}

private Set<String> getSourceFileHashes(CodeProject project) {
    // Only include source files, exclude:
    // - node_modules/, target/, build/, dist/, .gradle/
    // - .class, .jar, .war files
    // - IDE files (.idea/, .vscode/, *.iml)
    // - OS files (.DS_Store, Thumbs.db)

    return project.getFiles().stream()
        .filter(this::isSourceFile)
        .map(ScannedFile::getFileHash)
        .map(FileHash::getSha256)
        .collect(Collectors.toSet());
}

private DiffComplexity estimateDiffComplexity(CodeProject a, CodeProject b) {
    int totalFiles = Math.max(a.getSourceFileCount(), b.getSourceFileCount());
    int diffFiles = Math.abs(a.getSourceFileCount() - b.getSourceFileCount());

    double diffRatio = (double) diffFiles / totalFiles;

    if (diffRatio < 0.05) return DiffComplexity.TRIVIAL;  // < 5% different
    if (diffRatio < 0.15) return DiffComplexity.SIMPLE;   // < 15% different
    if (diffRatio < 0.30) return DiffComplexity.MEDIUM;   // < 30% different
    return DiffComplexity.COMPLEX;                         // > 30% different
}
```

## Archive Organization

Store code projects in `/Archive/Code/` organized by project type and identity:

```
/Archive/Code/
├── Java/
│   ├── com.example/
│   │   ├── my-api/
│   │   │   ├── 1.0.0/                    ← Version folder
│   │   │   ├── 2.0.0/
│   │   │   └── 1.0.0-variant1/           ← Same version, different content
│   │   └── other-project/
│   │       └── 1.0.0/
├── JavaScript/
│   ├── @myorg/
│   │   └── my-package/
│   │       ├── 2.3.1/
│   │       └── 2.4.0/
│   └── standalone-app/
│       └── 1.0.0/
├── Python/
│   └── my-package/
│       └── 0.1.0/
├── Go/
│   └── github.com/
│       └── user/
│           └── myproject/
│               └── main/                  ← Branch name for Git projects
├── Rust/
│   └── my-crate/
│       └── 0.1.0/
├── Git/
│   └── github.com/
│       └── user/
│           └── repo/
│               ├── main/
│               ├── feature-xyz/
│               └── develop/
└── Unknown/
    └── my-scripts/
        └── 2024-01-15/                    ← Use scan date
```

### Path Generation Algorithm

```java
public Path generateArchivePath(CodeProject project) {
    return switch (project.getType()) {
        case MAVEN, GRADLE -> Path.of(
            "/Archive/Code/Java",
            project.getGroupId().replace('.', '/'),
            project.getName(),
            getVersionOrVariant(project)
        );

        case NPM -> {
            String[] parts = project.getName().split("/");
            if (parts.length == 2) { // Scoped package
                yield Path.of(
                    "/Archive/Code/JavaScript",
                    parts[0], // @scope
                    parts[1],
                    project.getVersion()
                );
            } else {
                yield Path.of(
                    "/Archive/Code/JavaScript",
                    project.getName(),
                    project.getVersion()
                );
            }
        }

        case GO -> {
            // Module path is like github.com/user/repo
            yield Path.of(
                "/Archive/Code/Go",
                project.getName(), // Already includes full path
                project.getGitBranch() != null ? project.getGitBranch() : "main"
            );
        }

        case PYTHON, RUST -> Path.of(
            "/Archive/Code/" + project.getType().getDisplayName(),
            project.getName(),
            project.getVersion()
        );

        case GIT -> {
            // Extract owner/repo from remote URL
            String repoPath = extractRepoPath(project.getGitRemote());
            yield Path.of(
                "/Archive/Code/Git",
                repoPath,
                project.getGitBranch()
            );
        }

        case GENERIC -> Path.of(
            "/Archive/Code/Unknown",
            project.getName(),
            project.getScanDate().toString() // Use scan date as "version"
        );
    };
}

private String getVersionOrVariant(CodeProject project) {
    // If this is a duplicate with different content, append variant suffix
    long duplicateCount = findDuplicatesWithSameVersion(project);
    if (duplicateCount > 0) {
        return project.getVersion() + "-variant" + (duplicateCount + 1);
    }
    return project.getVersion();
}
```

## Exclusions During Scanning

When scanning code projects, exclude common build artifacts and dependencies:

### Folders to Exclude

```java
private static final Set<String> EXCLUDED_FOLDERS = Set.of(
    // Java/Kotlin
    "target",           // Maven build output
    "build",            // Gradle build output
    ".gradle",          // Gradle cache
    "out",              // IntelliJ output

    // JavaScript/Node
    "node_modules",     // NPM dependencies
    "dist",             // Build output
    ".next",            // Next.js build
    ".nuxt",            // Nuxt.js build

    // Python
    "__pycache__",      // Python cache
    ".venv",            // Virtual environment
    "venv",
    ".pytest_cache",
    ".mypy_cache",

    // Go
    "vendor",           // Go vendor directory

    // Rust
    "target",           // Cargo build output

    // IDE
    ".idea",            // IntelliJ
    ".vscode",          // VS Code
    ".eclipse",

    // OS
    ".DS_Store",        // macOS
    "Thumbs.db"         // Windows
);
```

### File Extensions to Exclude

```java
private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
    // Compiled
    ".class",           // Java bytecode
    ".pyc",             // Python bytecode
    ".o",               // Object files

    // Archives
    ".jar",             // Java archives
    ".war",
    ".ear",

    // IDE
    ".iml",             // IntelliJ module

    // Logs
    ".log"
);
```

**Important:** These exclusions apply during:
1. File hash computation
2. Project content hash computation
3. Similarity calculation

But we still **scan and record** these files in the database (just don't use them for deduplication).

## Database Schema

```sql
-- New table for code projects
CREATE TABLE code_project (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES source(id),
    root_path TEXT NOT NULL,                -- Original path where project was found
    project_type VARCHAR(50) NOT NULL,      -- MAVEN, NPM, GO, etc.
    name TEXT NOT NULL,                     -- Project name
    version TEXT,                           -- Version (if available)
    group_id TEXT,                          -- For Maven/Gradle
    git_remote TEXT,                        -- For Git repos
    git_branch TEXT,                        -- For Git repos
    git_commit TEXT,                        -- For Git repos
    identifier TEXT NOT NULL,               -- Computed identifier
    content_hash TEXT NOT NULL,             -- Hash of all source file hashes
    source_file_count INT NOT NULL,         -- Number of source files
    total_file_count INT NOT NULL,          -- Total files including artifacts
    total_size_bytes BIGINT NOT NULL,
    scanned_at TIMESTAMP NOT NULL,
    archive_path TEXT,                      -- Target path in /Archive/Code

    CONSTRAINT uq_code_project_source_path UNIQUE(source_id, root_path)
);

CREATE INDEX idx_code_project_identifier ON code_project(identifier);
CREATE INDEX idx_code_project_content_hash ON code_project(content_hash);
CREATE INDEX idx_code_project_type ON code_project(project_type);

-- Link files to code projects
ALTER TABLE scanned_file ADD COLUMN code_project_id BIGINT REFERENCES code_project(id);
CREATE INDEX idx_scanned_file_code_project ON scanned_file(code_project_id);

-- Track code project duplicates
CREATE TABLE code_project_duplicate_group (
    id BIGSERIAL PRIMARY KEY,
    identifier TEXT NOT NULL,               -- Project identifier
    duplicate_type VARCHAR(50) NOT NULL,    -- EXACT, SAME_PROJECT_DIFF_CONTENT, DIFFERENT_VERSION
    similarity_percent DECIMAL(5,2),        -- Similarity for non-exact duplicates
    files_only_in_primary INT,
    files_only_in_secondary INT,
    files_in_both INT,
    diff_complexity VARCHAR(20),            -- TRIVIAL, SIMPLE, MEDIUM, COMPLEX
    resolution_status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, KEEP_BOTH, KEEP_PRIMARY, MERGED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE code_project_duplicate_member (
    id BIGSERIAL PRIMARY KEY,
    duplicate_group_id BIGINT NOT NULL REFERENCES code_project_duplicate_group(id),
    code_project_id BIGINT NOT NULL REFERENCES code_project(id),
    is_primary BOOLEAN DEFAULT FALSE,       -- Which one to keep
    CONSTRAINT uq_duplicate_member UNIQUE(duplicate_group_id, code_project_id)
);
```

## UI Requirements

### Code Projects View

Show list of detected code projects:

```
┌─────────────────────────────────────────────────────────────┐
│ Code Projects                                    [Refresh]  │
├─────────────────────────────────────────────────────────────┤
│ Type    │ Project                    │ Version │ Source     │
├─────────┼────────────────────────────┼─────────┼───────────┤
│ Maven   │ com.example:my-api         │ 1.0.0   │ Disk1      │
│ Maven   │ com.example:my-api         │ 1.0.0   │ Disk3 ⚠️   │  ← Duplicate
│ NPM     │ @myorg/my-package          │ 2.3.1   │ Disk1      │
│ Go      │ github.com/user/myproject  │ main    │ Disk2      │
│ Python  │ my-package                 │ 0.1.0   │ Disk1      │
│ Unknown │ my-scripts                 │ -       │ Disk4      │
└─────────────────────────────────────────────────────────────┘
```

### Duplicate Review

For projects flagged as duplicates:

```
┌─────────────────────────────────────────────────────────────┐
│ Code Project Duplicate: com.example:my-api:1.0.0            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Project 1: /Disk1/Projects/my-api                           │
│   Scanned: 2024-01-15                                       │
│   Files: 127 source files (1,234 total)                     │
│   Size: 45 MB                                               │
│                                                             │
│ Project 2: /Disk3/Backup/Projects/my-api                    │
│   Scanned: 2024-01-20                                       │
│   Files: 142 source files (1,289 total)                     │
│   Size: 48 MB                                               │
│                                                             │
│ Similarity: 87.3%                                           │
│ Difference: Medium complexity                               │
│   - 15 files only in Project 2                              │
│   - 8 files only in Project 1                               │
│   - 112 files in both                                       │
│                                                             │
│ Recommendation: Same project, likely different branches     │
│                 or uncommitted changes                      │
│                                                             │
│ Actions:                                                    │
│ [ Keep Both ]  [ Keep Project 1 ]  [ Keep Project 2 ]      │
│                                                             │
│ Archive as:                                                 │
│ /Archive/Code/Java/com/example/my-api/1.0.0                 │
│ /Archive/Code/Java/com/example/my-api/1.0.0-variant1       │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Plan

### Phase 1: Project Detection (Scanner)
1. Create `ProjectDetector` interface
2. Implement detectors for each project type:
   - `MavenProjectDetector`
   - `GradleProjectDetector`
   - `NpmProjectDetector`
   - `GoProjectDetector`
   - `PythonProjectDetector`
   - `RustProjectDetector`
   - `GitProjectDetector`
   - `GenericCodeDetector`
3. Create `ProjectIdentity` model
4. Integrate into folder scanning
5. Send project metadata to server

### Phase 2: Server Storage & API
1. Create database schema (migration)
2. Create `CodeProject` entity
3. Create `CodeProjectService`
4. Create REST endpoints:
   - `POST /api/code-projects` (from scanner)
   - `GET /api/code-projects` (list)
   - `GET /api/code-projects/{id}` (details)
   - `GET /api/code-projects/duplicates` (duplicate groups)
   - `POST /api/code-projects/duplicates/{groupId}/resolve` (resolve)

### Phase 3: Duplicate Detection
1. Implement content hash computation
2. Implement similarity calculation
3. Create duplicate grouping logic
4. Create resolution strategies

### Phase 4: UI
1. Add Code Projects view
2. Add duplicate review UI
3. Add project detail view
4. Integrate with archive path preview

### Phase 5: Archive Organization
1. Implement archive path generation
2. Integrate with migration process
3. Test end-to-end flow

## Testing Strategy

### Unit Tests

Test each project detector:

```java
@Test
void testMavenDetection() {
    Path projectRoot = createTempMavenProject(
        "com.example", "my-api", "1.0.0"
    );

    Optional<ProjectIdentity> identity = mavenDetector.detect(projectRoot);

    assertTrue(identity.isPresent());
    assertEquals("com.example:my-api:1.0.0", identity.get().getIdentifier());
}
```

### Integration Tests

Test full workflow:

```java
@Test
void testCodeProjectScanAndDuplicate() {
    // Scan two copies of same project
    scanFolder("/test-data/my-api-copy1");
    scanFolder("/test-data/my-api-copy2");

    // Should detect 2 projects
    List<CodeProject> projects = codeProjectService.findAll();
    assertEquals(2, projects.size());

    // Should detect duplicate
    List<CodeProjectDuplicateGroup> duplicates =
        codeProjectService.findDuplicates();
    assertEquals(1, duplicates.size());
}
```

## Future Enhancements

### Git Integration

Instead of archiving, push to git server:

```java
public void archiveCodeProject(CodeProject project) {
    if (project.hasGitHistory()) {
        // Push existing repo
        gitService.pushToArchiveServer(project);
    } else {
        // Initialize new repo
        gitService.createRepoAndPush(project);
    }
}
```

### Dependency Analysis

Analyze project dependencies to understand relationships:

```
Project: my-api (1.0.0)
  Dependencies:
    - my-common-lib (2.1.0) ← Also found in archive
    - spring-boot (3.2.0)
```

### Code Search

Enable full-text search within code:

```
Search in code projects:
  "class UserService" → Found in 3 projects
```
