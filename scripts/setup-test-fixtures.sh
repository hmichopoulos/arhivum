#!/bin/bash
# Setup test fixtures for Archivum development
# Downloads from remote or generates locally if unavailable
#
# Usage: ./scripts/setup-test-fixtures.sh
#
# Environment variables:
#   ARCHIVUM_FIXTURES_URL - Base URL for fixture downloads (optional)
#   ARCHIVUM_TEST_FIXTURES - Target directory (default: ~/test-fixtures)

set -e

FIXTURES_DIR="${ARCHIVUM_TEST_FIXTURES:-$HOME/test-fixtures}"
FIXTURES_URL="${ARCHIVUM_FIXTURES_URL:-}"
MARKER_FILE="$FIXTURES_DIR/.fixtures-ready"

# Check if fixtures already exist
check_fixtures_exist() {
    if [ -f "$MARKER_FILE" ]; then
        echo "Test fixtures already set up at: $FIXTURES_DIR"
        return 0
    fi
    return 1
}

# Download and extract a fixture zip file
download_fixture() {
    local name="$1"
    local url="$2"
    local target="$FIXTURES_DIR/$name"

    if [ -d "$target" ]; then
        echo "  Skipping $name (already exists)"
        return 0
    fi

    echo "  Downloading $name..."
    local tmpfile
    tmpfile=$(mktemp)
    if curl -fsSL "$url" -o "$tmpfile" 2>/dev/null; then
        mkdir -p "$target"
        unzip -q "$tmpfile" -d "$target"
        rm "$tmpfile"
        echo "  Downloaded and extracted: $name"
        return 0
    fi
    rm -f "$tmpfile"
    return 1
}

# Try to download all fixtures from remote URL
download_all_fixtures() {
    if [ -z "$FIXTURES_URL" ]; then
        return 1
    fi

    echo "Attempting to download fixtures from: $FIXTURES_URL"
    local success=true

    download_fixture "scenario-valid-files" "$FIXTURES_URL/scenario-valid-files.zip" || success=false
    download_fixture "scenario-nested-structure" "$FIXTURES_URL/scenario-nested-structure.zip" || success=false
    download_fixture "scenario-code-projects" "$FIXTURES_URL/scenario-code-projects.zip" || success=false
    download_fixture "scenario-duplicates" "$FIXTURES_URL/scenario-duplicates.zip" || success=false

    if [ "$success" = true ]; then
        return 0
    fi
    return 1
}

# Generate a text file with random content
generate_text_file() {
    local path="$1"
    local lines="${2:-10}"
    mkdir -p "$(dirname "$path")"
    for i in $(seq 1 "$lines"); do
        echo "Line $i: Sample content generated at $(date -Iseconds) - $RANDOM"
    done > "$path"
}

# Generate scenario: valid files with various extensions
generate_valid_files() {
    local dir="$FIXTURES_DIR/scenario-valid-files"
    echo "  Generating: scenario-valid-files"
    mkdir -p "$dir"

    generate_text_file "$dir/document.txt" 20
    generate_text_file "$dir/notes.md" 15
    generate_text_file "$dir/data.csv" 10
    generate_text_file "$dir/config.json" 5
    generate_text_file "$dir/script.sh" 8
    echo '{"name": "test", "version": "1.0"}' > "$dir/package.json"
    echo "SELECT * FROM users;" > "$dir/query.sql"
}

# Generate scenario: nested directory structure
generate_nested_structure() {
    local dir="$FIXTURES_DIR/scenario-nested-structure"
    echo "  Generating: scenario-nested-structure"
    mkdir -p "$dir/level1/level2/level3/level4"
    mkdir -p "$dir/photos/2024/vacation"
    mkdir -p "$dir/documents/work/projects"

    generate_text_file "$dir/root-file.txt" 5
    generate_text_file "$dir/level1/file-l1.txt" 5
    generate_text_file "$dir/level1/level2/file-l2.txt" 5
    generate_text_file "$dir/level1/level2/level3/file-l3.txt" 5
    generate_text_file "$dir/level1/level2/level3/level4/file-l4.txt" 5
    generate_text_file "$dir/photos/2024/vacation/notes.txt" 3
    generate_text_file "$dir/documents/work/projects/readme.txt" 10
}

# Generate scenario: code project structures
generate_code_projects() {
    local dir="$FIXTURES_DIR/scenario-code-projects"
    echo "  Generating: scenario-code-projects"

    # Node.js project
    local node_dir="$dir/node-project"
    mkdir -p "$node_dir/src" "$node_dir/node_modules/.bin"
    echo '{"name": "sample-app", "version": "1.0.0"}' > "$node_dir/package.json"
    echo 'console.log("Hello");' > "$node_dir/src/index.js"
    echo '// placeholder' > "$node_dir/node_modules/.bin/placeholder"

    # Java/Gradle project
    local java_dir="$dir/java-project"
    mkdir -p "$java_dir/src/main/java" "$java_dir/build"
    echo "plugins { id 'java' }" > "$java_dir/build.gradle"
    echo "rootProject.name = 'sample'" > "$java_dir/settings.gradle"
    echo 'public class Main {}' > "$java_dir/src/main/java/Main.java"

    # Git repository
    local git_dir="$dir/git-repo"
    mkdir -p "$git_dir/.git/objects" "$git_dir/.git/refs"
    echo "ref: refs/heads/main" > "$git_dir/.git/HEAD"
    generate_text_file "$git_dir/README.md" 5
}

# Generate scenario: duplicate files for dedup testing
generate_duplicates() {
    local dir="$FIXTURES_DIR/scenario-duplicates"
    echo "  Generating: scenario-duplicates"
    mkdir -p "$dir/folder-a" "$dir/folder-b" "$dir/folder-c"

    # Create identical files in different locations
    local content="This is duplicate content for testing - fixed seed"
    echo "$content" > "$dir/folder-a/duplicate.txt"
    echo "$content" > "$dir/folder-b/duplicate.txt"
    echo "$content" > "$dir/folder-c/same-content.txt"

    # Unique files
    generate_text_file "$dir/folder-a/unique-a.txt" 5
    generate_text_file "$dir/folder-b/unique-b.txt" 7
}

# Generate scenario: empty directory
generate_empty_scenario() {
    local dir="$FIXTURES_DIR/scenario-empty"
    echo "  Generating: scenario-empty"
    mkdir -p "$dir"
}

# Generate all fixtures locally
generate_all_fixtures() {
    echo "Generating test fixtures locally..."
    generate_valid_files
    generate_nested_structure
    generate_code_projects
    generate_duplicates
    generate_empty_scenario
}

# Main entry point
main() {
    echo "=== Archivum Test Fixtures Setup ==="
    echo "Target directory: $FIXTURES_DIR"
    echo ""

    if check_fixtures_exist; then
        exit 0
    fi

    mkdir -p "$FIXTURES_DIR"

    if download_all_fixtures; then
        echo "Downloaded fixtures successfully"
    else
        echo "Download unavailable, generating fixtures locally..."
        generate_all_fixtures
    fi

    # Create marker file
    date -Iseconds > "$MARKER_FILE"
    echo ""
    echo "Test fixtures ready at: $FIXTURES_DIR"
    ls -la "$FIXTURES_DIR"
}

main "$@"
