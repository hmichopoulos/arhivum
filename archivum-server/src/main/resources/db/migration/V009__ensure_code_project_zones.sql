-- V009__ensure_code_project_zones.sql
-- Safety migration: Ensure all code project folders have explicit CODE zone set
-- This is idempotent and safe to run multiple times

INSERT INTO folder_zone (id, source_id, folder_path, zone)
SELECT
    uuid_generate_v4(),
    cp.source_id,
    cp.root_path,
    'CODE'
FROM code_project cp
WHERE NOT EXISTS (
    SELECT 1 FROM folder_zone fz
    WHERE fz.source_id = cp.source_id
    AND fz.folder_path = cp.root_path
)
ON CONFLICT (source_id, folder_path) DO NOTHING;

-- Add comment for documentation
COMMENT ON TABLE folder_zone IS 'Folder-level zone classifications for deduplication rules. Code project folders are automatically set to CODE zone.';
