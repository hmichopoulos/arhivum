-- V018__create_destination_table.sql
-- Create destination table for managing migration targets

CREATE TABLE destination (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    destination_type VARCHAR(50) NOT NULL,  -- NAS, WAREHOUSE, STAGING

    -- Filesystem location
    mounted_path VARCHAR(1000) NOT NULL,    -- The mounted folder path (e.g., /mnt/nas/archive, /mnt/warehouse/disk1)

    -- Physical binding
    physical_identifier VARCHAR(500),       -- Identifier to bind to real hardware (e.g., disk UUID, NAS hostname, label)

    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,

    -- Capacity (optional manual override, actual space monitored in real-time from filesystem)
    total_capacity_bytes BIGINT,            -- Optional: manually set total capacity, otherwise read from filesystem

    -- Priority for auto-selection (higher = preferred)
    priority INTEGER NOT NULL DEFAULT 0,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT destination_type_check CHECK (destination_type IN ('NAS', 'WAREHOUSE', 'STAGING'))
);

-- Indexes
CREATE UNIQUE INDEX idx_destination_mounted_path ON destination(mounted_path);
CREATE INDEX idx_destination_type ON destination(destination_type);
CREATE INDEX idx_destination_active ON destination(is_active);
CREATE INDEX idx_destination_priority ON destination(priority DESC);

-- Comments
COMMENT ON TABLE destination IS 'Configured migration destinations (mounted filesystem paths)';
COMMENT ON COLUMN destination.destination_type IS 'Type of destination: NAS (final archive), WAREHOUSE (large files on HDDs), STAGING (temporary before final location)';
COMMENT ON COLUMN destination.mounted_path IS 'Mounted filesystem path where files will be written (e.g., /mnt/nas/archive, /mnt/warehouse/disk1)';
COMMENT ON COLUMN destination.physical_identifier IS 'Identifier to bind to physical hardware (e.g., disk UUID, NAS hostname, physical label on device)';
COMMENT ON COLUMN destination.is_active IS 'Whether this destination is currently available for migrations';
COMMENT ON COLUMN destination.priority IS 'Higher priority destinations are preferred for auto-selection';
COMMENT ON COLUMN destination.total_capacity_bytes IS 'Optional manually set total capacity; if null, read from filesystem. Available space is always read in real-time from filesystem.';
