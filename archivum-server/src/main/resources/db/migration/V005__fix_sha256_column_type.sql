-- Migration V005: Fix sha256 column type from CHAR to VARCHAR
--
-- PROBLEM: The sha256 column was created as CHAR(64) in earlier migrations, but Hibernate
-- entity definition uses VARCHAR(64). CHAR(n) pads values with spaces to reach fixed length,
-- which causes issues:
--   1. Hash comparisons fail (trailing spaces don't match)
--   2. Duplicate detection breaks (hashes are incorrectly treated as different)
--   3. JPA entity validation fails on mismatch between DB and Java types
--
-- SOLUTION: Convert CHAR(64) to VARCHAR(64) to match entity definition.
-- This is safe because VARCHAR stores exact values without padding.
--
-- DATA SAFETY: PostgreSQL's ALTER COLUMN TYPE preserves existing data and automatically
-- trims trailing spaces during conversion. No data corruption or loss will occur.
--
-- IMPACT: After this migration:
--   - Existing hashes will have trailing spaces removed
--   - New hashes will be stored without padding
--   - Duplicate detection will work correctly
--   - All hash-based operations (findBySha256) will function properly

ALTER TABLE scanned_file
  ALTER COLUMN sha256 TYPE VARCHAR(64);
