-- Add folder_zone table for storing explicit folder zone classifications
CREATE TABLE folder_zone (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    folder_path VARCHAR(1000) NOT NULL,
    zone VARCHAR(50) NOT NULL,
    CONSTRAINT uq_folder_zone_source_path UNIQUE (source_id, folder_path)
);

-- Index for faster lookups by source
CREATE INDEX idx_folder_zone_source ON folder_zone(source_id);

-- Index for faster lookups by path prefix (for inheritance queries)
CREATE INDEX idx_folder_zone_path ON folder_zone(source_id, folder_path);
