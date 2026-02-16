-- V016__convert_file_state_to_varchar.sql
-- Convert file_state from PostgreSQL enum to VARCHAR for Hibernate compatibility

-- Step 1: Drop the default value (which depends on the enum type)
ALTER TABLE scanned_file
  ALTER COLUMN state DROP DEFAULT;

-- Step 2: Alter the column to VARCHAR, casting the existing enum values
ALTER TABLE scanned_file
  ALTER COLUMN state TYPE VARCHAR(20)
  USING state::text;

-- Step 3: Set the new default value (as text)
ALTER TABLE scanned_file
  ALTER COLUMN state SET DEFAULT 'DISCOVERED';

-- Step 4: Drop the enum type (no longer needed)
DROP TYPE file_state;

-- Step 5: Add a check constraint to maintain data integrity
ALTER TABLE scanned_file
  ADD CONSTRAINT file_state_check
  CHECK (state IN ('DISCOVERED', 'PINNED', 'STAGED', 'MIGRATING', 'MIGRATED', 'WAREHOUSED', 'DELETED'));

-- Add comment
COMMENT ON COLUMN scanned_file.state IS 'Current state in migration workflow: DISCOVERED (found during scan), PINNED (already in final location), STAGED (ready to migrate), MIGRATING (currently being migrated), MIGRATED (successfully migrated), WAREHOUSED (on warehouse disk), DELETED (marked for deletion)';
