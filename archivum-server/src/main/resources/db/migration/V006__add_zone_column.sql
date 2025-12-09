-- Add zone column to scanned_file table
ALTER TABLE scanned_file
ADD COLUMN zone VARCHAR(50) DEFAULT 'UNKNOWN';

-- Update existing rows to have UNKNOWN zone
UPDATE scanned_file
SET zone = 'UNKNOWN'
WHERE zone IS NULL;
