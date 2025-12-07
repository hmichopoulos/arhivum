-- Create source table for tracking scanned disks, partitions, and archives
CREATE TABLE source (
    id UUID PRIMARY KEY,
    parent_id UUID,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    root_path TEXT NOT NULL,
    physical_id JSONB,
    status VARCHAR(50) NOT NULL,
    postponed BOOLEAN DEFAULT FALSE NOT NULL,
    total_files BIGINT DEFAULT 0 NOT NULL,
    total_size BIGINT DEFAULT 0 NOT NULL,
    processed_files BIGINT DEFAULT 0 NOT NULL,
    processed_size BIGINT DEFAULT 0 NOT NULL,
    scan_started_at TIMESTAMP,
    scan_completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    notes TEXT,

    -- Self-referential foreign key for parent-child relationships
    CONSTRAINT fk_source_parent FOREIGN KEY (parent_id)
        REFERENCES source(id) ON DELETE CASCADE
);

-- Index for parent-child queries
CREATE INDEX idx_source_parent_id ON source(parent_id);

-- Index for filtering by type
CREATE INDEX idx_source_type ON source(type);

-- Index for filtering by status
CREATE INDEX idx_source_status ON source(status);

-- Now that source table exists, add foreign key to code_project
ALTER TABLE code_project
    ADD CONSTRAINT fk_code_project_source
    FOREIGN KEY (source_id) REFERENCES source(id) ON DELETE CASCADE;

-- Add index for code_project source lookups
CREATE INDEX idx_code_project_source_id ON code_project(source_id);
