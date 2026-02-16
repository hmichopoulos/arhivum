-- V017__increase_extension_length.sql
-- Increase extension field length to handle longer file extensions

-- File extensions can be quite long (e.g., .tar.gz.backup, .component.tsx, etc.)
ALTER TABLE scanned_file
  ALTER COLUMN extension TYPE VARCHAR(100);

COMMENT ON COLUMN scanned_file.extension IS 'File extension (without dot), up to 100 characters for compound extensions';
