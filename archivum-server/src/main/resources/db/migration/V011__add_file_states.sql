-- V011__add_file_states.sql
-- Add file state tracking for migration workflow

-- Create enum for file states
CREATE TYPE file_state AS ENUM (
  'DISCOVERED',   -- Found during scan (default)
  'PINNED',       -- Already in final location (don't migrate)
  'STAGED',       -- Ready to migrate
  'MIGRATING',    -- Currently being migrated
  'MIGRATED',     -- Successfully migrated
  'WAREHOUSED',   -- On warehouse disk
  'DELETED'       -- Marked for deletion
);

-- Add state column to scanned_file table
ALTER TABLE scanned_file ADD COLUMN state file_state DEFAULT 'DISCOVERED';

-- Add migrated_path column (where file was migrated to)
ALTER TABLE scanned_file ADD COLUMN migrated_path VARCHAR(1000);

-- Add duplicate_of column (if file is duplicate, points to kept file)
ALTER TABLE scanned_file ADD COLUMN duplicate_of UUID REFERENCES scanned_file(id);

-- Create index on state for filtering
CREATE INDEX idx_scanned_file_state ON scanned_file(state);

-- Create index on duplicate_of for finding duplicates
CREATE INDEX idx_scanned_file_duplicate_of ON scanned_file(duplicate_of);

-- Add comments
COMMENT ON COLUMN scanned_file.state IS 'Current state in migration workflow';
COMMENT ON COLUMN scanned_file.migrated_path IS 'Destination path after successful migration';
COMMENT ON COLUMN scanned_file.duplicate_of IS 'If this file is a duplicate, references the file to keep';
