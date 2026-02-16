-- V015__convert_source_type_to_varchar.sql
-- Convert source_type from PostgreSQL enum to VARCHAR for Hibernate compatibility

-- Step 1: Drop the default value (which depends on the enum type)
ALTER TABLE source
  ALTER COLUMN source_type DROP DEFAULT;

-- Step 2: Alter the column to VARCHAR, casting the existing enum values
ALTER TABLE source
  ALTER COLUMN source_type TYPE VARCHAR(20)
  USING source_type::text;

-- Step 3: Set the new default value (as text)
ALTER TABLE source
  ALTER COLUMN source_type SET DEFAULT 'DISCOVERY';

-- Step 4: Drop the enum type (no longer needed)
DROP TYPE source_type;

-- Step 5: Add a check constraint to maintain data integrity
ALTER TABLE source
  ADD CONSTRAINT source_type_check
  CHECK (source_type IN ('DISCOVERY', 'DESTINATION', 'WAREHOUSE'));

-- Add comment
COMMENT ON COLUMN source.source_type IS 'DISCOVERY: scan to migrate, DESTINATION: already organized (auto-pin files), WAREHOUSE: catalog only (files stay on disk)';
