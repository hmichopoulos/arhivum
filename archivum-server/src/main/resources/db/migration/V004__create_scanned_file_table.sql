-- Migration V004: Create scanned_file table
-- Adds support for ingesting file metadata from scanner CLI

-- Scanned files table
CREATE TABLE scanned_file (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,

    -- Original location
    path TEXT NOT NULL,
    name VARCHAR(255) NOT NULL,
    extension VARCHAR(50),

    -- File properties
    size BIGINT NOT NULL,
    sha256 CHAR(64),
    modified_at TIMESTAMP,
    created_at TIMESTAMP,
    accessed_at TIMESTAMP,

    -- Content metadata
    mime_type VARCHAR(100),
    exif_metadata JSONB,

    -- Processing state
    status VARCHAR(50) NOT NULL DEFAULT 'HASHED',
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    original_file_id UUID,

    -- Timestamps
    scanned_at TIMESTAMP NOT NULL,
    created_at_db TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraints
    CONSTRAINT fk_scanned_file_source FOREIGN KEY (source_id)
        REFERENCES source(id) ON DELETE CASCADE,

    CONSTRAINT fk_scanned_file_original FOREIGN KEY (original_file_id)
        REFERENCES scanned_file(id) ON DELETE SET NULL,

    -- Unique constraint: one entry per file path per source
    CONSTRAINT uq_scanned_file_source_path UNIQUE (source_id, path)
);

-- Indexes for performance

-- 1. Source lookups (list all files from a source)
CREATE INDEX idx_scanned_file_source_id ON scanned_file(source_id);

-- 2. Hash lookups (duplicate detection)
-- Partial index: only index files that have hashes
CREATE INDEX idx_scanned_file_sha256 ON scanned_file(sha256)
    WHERE sha256 IS NOT NULL;

-- 3. Duplicate queries
CREATE INDEX idx_scanned_file_is_duplicate ON scanned_file(is_duplicate);

-- 4. Original file lookups (for duplicate chains)
CREATE INDEX idx_scanned_file_original_file_id ON scanned_file(original_file_id)
    WHERE original_file_id IS NOT NULL;

-- 5. Status queries
CREATE INDEX idx_scanned_file_status ON scanned_file(status);

-- 6. Extension-based queries (find all JPGs, PDFs, etc.)
-- Partial index: only index files with extensions
CREATE INDEX idx_scanned_file_extension ON scanned_file(extension)
    WHERE extension IS NOT NULL;

-- 7. MIME type queries
CREATE INDEX idx_scanned_file_mime_type ON scanned_file(mime_type)
    WHERE mime_type IS NOT NULL;

-- 8. Composite index for source+status queries (common pattern)
CREATE INDEX idx_scanned_file_source_status ON scanned_file(source_id, status);

-- Comments for documentation
COMMENT ON TABLE scanned_file IS 'Files discovered during scanning with metadata and hashes';
COMMENT ON COLUMN scanned_file.path IS 'Relative path within source (e.g., Photos/2015/vacation.jpg)';
COMMENT ON COLUMN scanned_file.sha256 IS 'SHA-256 hash in lowercase hex (64 chars)';
COMMENT ON COLUMN scanned_file.exif_metadata IS 'Full EXIF metadata object as JSONB (cameraMake, gps, etc.)';
COMMENT ON COLUMN scanned_file.is_duplicate IS 'Flag set by scanner during scan (tracks hashes in-memory)';
COMMENT ON COLUMN scanned_file.original_file_id IS 'Links to the file we are keeping (if this is a duplicate)';
COMMENT ON COLUMN scanned_file.scanned_at IS 'When scanner processed this file';
COMMENT ON COLUMN scanned_file.created_at_db IS 'When this record was created in database';
