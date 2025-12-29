-- V012__add_source_types.sql
-- Add source type to distinguish between different scanning purposes

-- Create enum for source types
CREATE TYPE source_type AS ENUM (
  'DISCOVERY',    -- Scan to find files to migrate (default)
  'DESTINATION',  -- Already organized location (pin all files)
  'WAREHOUSE'     -- Warehouse disk (files stay here, catalog only)
);

-- Add source_type column to source table
ALTER TABLE source ADD COLUMN source_type source_type DEFAULT 'DISCOVERY';

-- Add comments
COMMENT ON COLUMN source.source_type IS 'DISCOVERY: scan to migrate, DESTINATION: already organized (auto-pin files), WAREHOUSE: catalog only (files stay on disk)';
