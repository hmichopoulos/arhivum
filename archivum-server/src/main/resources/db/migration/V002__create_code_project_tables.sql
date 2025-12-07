-- Migration for code project detection tables
-- This adds support for detecting and tracking code projects (Maven, NPM, Go, etc.)

-- Table for code projects
CREATE TABLE code_project (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_id UUID NOT NULL,                        -- Reference to source (will be added later)
    root_path TEXT NOT NULL,                        -- Original path where project was found
    project_type VARCHAR(50) NOT NULL,              -- MAVEN, NPM, GO, PYTHON, RUST, GIT, GENERIC
    name TEXT NOT NULL,                             -- Project name
    version TEXT,                                   -- Version (if available)
    group_id TEXT,                                  -- For Maven/Gradle (e.g., com.example)
    git_remote TEXT,                                -- For Git repos
    git_branch TEXT,                                -- For Git repos
    git_commit TEXT,                                -- For Git repos (short SHA)
    identifier TEXT NOT NULL,                       -- Computed identifier (e.g., com.example:my-api:1.0.0)
    content_hash TEXT NOT NULL,                     -- Hash of all source file hashes
    source_file_count INTEGER NOT NULL,             -- Number of source files
    total_file_count INTEGER NOT NULL,              -- Total files including artifacts
    total_size_bytes BIGINT NOT NULL,
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archive_path TEXT,                              -- Target path in /Archive/Code
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for code_project
CREATE INDEX idx_code_project_identifier ON code_project(identifier);
CREATE INDEX idx_code_project_content_hash ON code_project(content_hash);
CREATE INDEX idx_code_project_type ON code_project(project_type);
CREATE UNIQUE INDEX idx_code_project_source_path ON code_project(source_id, root_path);

-- Table for code project duplicate groups
CREATE TABLE code_project_duplicate_group (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    identifier TEXT NOT NULL,                       -- Project identifier (same for all members)
    duplicate_type VARCHAR(50) NOT NULL,            -- EXACT, SAME_PROJECT_DIFF_CONTENT, DIFFERENT_VERSION
    similarity_percent DECIMAL(5,2),                -- Similarity percentage (0-100)
    files_only_in_primary INTEGER,                  -- Files only in primary project
    files_only_in_secondary INTEGER,                -- Files only in secondary project
    files_in_both INTEGER,                          -- Files in both projects
    diff_complexity VARCHAR(20),                    -- TRIVIAL, SIMPLE, MEDIUM, COMPLEX
    resolution_status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, KEEP_BOTH, KEEP_PRIMARY, MERGED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for duplicate groups
CREATE INDEX idx_duplicate_group_identifier ON code_project_duplicate_group(identifier);
CREATE INDEX idx_duplicate_group_status ON code_project_duplicate_group(resolution_status);

-- Table for members of duplicate groups
CREATE TABLE code_project_duplicate_member (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    duplicate_group_id UUID NOT NULL REFERENCES code_project_duplicate_group(id) ON DELETE CASCADE,
    code_project_id UUID NOT NULL REFERENCES code_project(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,      -- Which one to keep (if resolved)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_duplicate_member UNIQUE(duplicate_group_id, code_project_id)
);

-- Indexes for duplicate members
CREATE INDEX idx_duplicate_member_group ON code_project_duplicate_member(duplicate_group_id);
CREATE INDEX idx_duplicate_member_project ON code_project_duplicate_member(code_project_id);

-- Comments for documentation
COMMENT ON TABLE code_project IS 'Detected code projects (Maven, NPM, Go, etc.)';
COMMENT ON COLUMN code_project.identifier IS 'Unique identifier format varies by type: Maven=groupId:artifactId:version, NPM=name:version, Git=remote@branch';
COMMENT ON COLUMN code_project.content_hash IS 'SHA-256 hash of sorted list of source file hashes (excludes build artifacts)';

COMMENT ON TABLE code_project_duplicate_group IS 'Groups of code projects that are duplicates or variants of each other';
COMMENT ON COLUMN code_project_duplicate_group.duplicate_type IS 'EXACT=identical content, SAME_PROJECT_DIFF_CONTENT=same identifier different files, DIFFERENT_VERSION=same project different version';

COMMENT ON TABLE code_project_duplicate_member IS 'Links code projects to their duplicate groups';
