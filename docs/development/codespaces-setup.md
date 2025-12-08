# GitHub Codespaces Development Setup

This guide explains how to use GitHub Codespaces for Archivum development and testing.

## Quick Start

### Creating a Codespace

1. Navigate to the repository on GitHub
2. Click the green **Code** button
3. Select the **Codespaces** tab
4. Click **Create codespace on [branch-name]**

For PR review:
1. Open the Pull Request
2. Click the **Code** button on the PR page
3. Select **Create codespace for this branch**

The Codespace will automatically:
- Install Java 21 and Node.js 20
- Start PostgreSQL database
- Build all Gradle projects
- Install the `archivum-scanner` CLI globally
- Install UI dependencies
- Set up test fixtures

## Environment Overview

### Ports

| Port | Service | Auto-Forward |
|------|---------|--------------|
| 8080 | Backend API | Notify |
| 3000 | UI (Vite) | Open Browser |
| 5432 | PostgreSQL | Silent |

### Pre-installed Tools

- `archivum-scanner` - CLI tool (available globally)
- `./gradlew` - Gradle wrapper for Java builds
- `npm` - Node.js package manager

## Test Fixtures

Test fixtures are automatically created at `~/test-fixtures/` and include several scenarios for testing the scanner.

### Fixture Locations

```
~/test-fixtures/
├── scenario-valid-files/       # Various file types
├── scenario-nested-structure/  # Deeply nested directories
├── scenario-code-projects/     # Code project structures
├── scenario-duplicates/        # Files with identical content
└── scenario-empty/             # Empty directory
```

### Scenario Descriptions

#### scenario-valid-files
Contains files with common extensions:
- `document.txt`, `notes.md` - Text files
- `data.csv`, `config.json` - Data files
- `script.sh`, `query.sql` - Code/script files
- `package.json` - Node.js manifest

#### scenario-nested-structure
Tests deep directory traversal:
- 4 levels of nested directories
- Simulated photo organization (photos/2024/vacation)
- Document hierarchy (documents/work/projects)

#### scenario-code-projects
Tests code project detection:
- `node-project/` - Node.js with package.json
- `java-project/` - Gradle project with build.gradle
- `git-repo/` - Git repository structure

#### scenario-duplicates
Tests duplicate file detection:
- Identical files in different folders
- Files with same content, different names

#### scenario-empty
Tests handling of empty directories.

## Running the Application

### Start the Backend

```bash
./gradlew :archivum-server:bootRun
```

The backend will start at `http://localhost:8080`.

### Start the UI

In a new terminal:

```bash
cd archivum-ui
npm run dev
```

The UI will open automatically at `http://localhost:3000`.

## Using the Scanner CLI

The `archivum-scanner` command is available globally after Codespace setup.

### Scan Test Fixtures

```bash
# Scan valid files scenario
archivum-scanner scan ~/test-fixtures/scenario-valid-files

# Scan nested structure
archivum-scanner scan ~/test-fixtures/scenario-nested-structure

# Scan code projects
archivum-scanner scan ~/test-fixtures/scenario-code-projects
```

### Upload Scan Results

After scanning, upload results to the server:

```bash
# Upload a specific scan
archivum-scanner upload ./archivum-scanner/output/<scan-id>

# Upload all scans
for dir in ./archivum-scanner/output/*/; do
  archivum-scanner upload "$dir"
done
```

## Development Workflow

### Rebuilding After Code Changes

```bash
# Rebuild all projects
./gradlew build

# Rebuild and reinstall CLI
./gradlew :archivum-scanner:installDist
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :archivum-server:test
./gradlew :archivum-scanner:test
```

### Database Access

Connect to PostgreSQL:
```bash
psql -h localhost -U archivum -d archivum
# Password: archivum
```

## Adding New Test Scenarios

### Option 1: Remote Fixtures (Preferred)

If you have access to the fixtures storage:

1. Create a zip file with your scenario structure
2. Upload to the configured storage location
3. Name it `scenario-<name>.zip`
4. Add download call in `scripts/setup-test-fixtures.sh`:
   ```bash
   download_fixture "scenario-<name>" "$FIXTURES_URL/scenario-<name>.zip"
   ```

### Option 2: Generated Fixtures

For scenarios that can be generated:

1. Add a new function in `scripts/setup-test-fixtures.sh`:
   ```bash
   generate_<scenario_name>() {
       local dir="$FIXTURES_DIR/scenario-<name>"
       echo "  Generating: scenario-<name>"
       mkdir -p "$dir"
       # Add file generation logic
   }
   ```

2. Call it from `generate_all_fixtures()`:
   ```bash
   generate_<scenario_name>
   ```

### Regenerating Fixtures

To regenerate fixtures from scratch:

```bash
rm -rf ~/test-fixtures
bash scripts/setup-test-fixtures.sh
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ARCHIVUM_TEST_FIXTURES` | Test fixtures directory | `~/test-fixtures` |
| `ARCHIVUM_FIXTURES_URL` | Remote fixtures base URL | (none) |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://db:5432/archivum` |

## Troubleshooting

### CLI Not Found

If `archivum-scanner` is not available:
```bash
./gradlew :archivum-scanner:installDist
sudo ln -sf "$PWD/archivum-scanner/build/install/archivum-scanner/bin/archivum-scanner" /usr/local/bin/
```

### Database Connection Issues

Check if PostgreSQL is running:
```bash
docker ps | grep postgres
```

Restart the database:
```bash
docker compose -f .devcontainer/docker-compose.yml restart db
```

### Port Already in Use

Check what's using a port:
```bash
lsof -i :8080
lsof -i :3000
```

### Fixtures Missing

Regenerate test fixtures:
```bash
rm ~/test-fixtures/.fixtures-ready
bash scripts/setup-test-fixtures.sh
```
