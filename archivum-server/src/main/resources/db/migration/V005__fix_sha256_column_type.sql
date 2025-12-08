-- Migration V005: Fix sha256 column type from CHAR to VARCHAR
-- Hibernate expects VARCHAR(64) but the column was created as CHAR(64)

ALTER TABLE scanned_file
  ALTER COLUMN sha256 TYPE VARCHAR(64);
