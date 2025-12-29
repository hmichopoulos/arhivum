-- V010__add_disk_tracking.sql
-- Add disk tracking fields to source table for offline workflow support

-- Add disk serial number (unique hardware identifier)
ALTER TABLE source ADD COLUMN serial_number VARCHAR(255);

-- Add disk state (ONLINE when plugged in, OFFLINE when unplugged)
ALTER TABLE source ADD COLUMN state VARCHAR(20) DEFAULT 'OFFLINE';

-- Add current mount point (e.g., /mnt/disk1) or null if offline
ALTER TABLE source ADD COLUMN mount_point VARCHAR(500);

-- Add last seen timestamp (last time disk was detected)
ALTER TABLE source ADD COLUMN last_seen TIMESTAMP;

-- Create index on serial number for fast lookup when disk is plugged in
CREATE INDEX idx_source_serial ON source(serial_number);

-- Add comments for documentation
COMMENT ON COLUMN source.serial_number IS 'Hardware serial number from disk (used to identify disk when plugged back in)';
COMMENT ON COLUMN source.state IS 'Current state: ONLINE (plugged in and accessible) or OFFLINE (unplugged)';
COMMENT ON COLUMN source.mount_point IS 'Current mount point (e.g., /mnt/disk1) or null if disk is offline';
COMMENT ON COLUMN source.last_seen IS 'Timestamp when disk was last detected (for tracking lost disks)';
