-- Add ignore_for_migration flag to scanned_file table
-- This allows marking files/folders to be excluded from migration while keeping them for duplicate checks

ALTER TABLE scanned_file
ADD COLUMN ignore_for_migration BOOLEAN NOT NULL DEFAULT FALSE;

-- Add index for efficient filtering of ignored files
CREATE INDEX idx_scanned_file_ignore ON scanned_file(ignore_for_migration);

-- Add composite index for source + ignore queries
CREATE INDEX idx_scanned_file_source_ignore ON scanned_file(source_id, ignore_for_migration);

-- Add comment explaining the flag
COMMENT ON COLUMN scanned_file.ignore_for_migration IS
'When true, this file/folder should not be migrated but is kept in DB for duplicate checking. Can be physically deleted from source.';
