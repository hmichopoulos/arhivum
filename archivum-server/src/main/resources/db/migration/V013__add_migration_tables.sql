-- V013__add_migration_tables.sql
-- Add migration planning and execution tables

-- Migration plan table
CREATE TABLE migration_plan (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    status VARCHAR(50) NOT NULL,  -- DRAFT, READY, EXECUTING, COMPLETE, FAILED
    total_files INTEGER NOT NULL DEFAULT 0,
    total_size_bytes BIGINT NOT NULL DEFAULT 0,
    destination_type VARCHAR(50) NOT NULL,  -- NAS, WAREHOUSE, GIT
    destination_base_path VARCHAR(1000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_migration_plan_status ON migration_plan(status);

-- Migration task table (individual file migrations)
CREATE TABLE migration_task (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES migration_plan(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES source(id),
    file_id UUID NOT NULL REFERENCES scanned_file(id),
    destination_path VARCHAR(1000) NOT NULL,
    status VARCHAR(50) NOT NULL,  -- PENDING, WAITING_FOR_DISK, IN_PROGRESS, COMPLETE, FAILED
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_migration_task_plan ON migration_task(plan_id);
CREATE INDEX idx_migration_task_status ON migration_task(status);
CREATE INDEX idx_migration_task_file ON migration_task(file_id);

-- File migration history (audit trail)
CREATE TABLE file_migration_history (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES scanned_file(id),
    from_location VARCHAR(500) NOT NULL,
    to_location VARCHAR(500) NOT NULL,
    migration_type VARCHAR(50) NOT NULL,  -- NAS_COPY, WAREHOUSE_COPY, NAS_TO_WAREHOUSE, WAREHOUSE_TO_NAS
    migrated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    migrated_by VARCHAR(100)
);

CREATE INDEX idx_migration_history_file ON file_migration_history(file_id);
CREATE INDEX idx_migration_history_migrated_at ON file_migration_history(migrated_at);

-- Comments
COMMENT ON TABLE migration_plan IS 'Migration plans grouping multiple file migrations';
COMMENT ON TABLE migration_task IS 'Individual file migration tasks within a plan';
COMMENT ON TABLE file_migration_history IS 'Audit trail of all file migrations';

COMMENT ON COLUMN migration_plan.status IS 'Plan status: DRAFT (being created), READY (ready to execute), EXECUTING (in progress), COMPLETE (finished), FAILED (failed)';
COMMENT ON COLUMN migration_task.status IS 'Task status: PENDING (not started), WAITING_FOR_DISK (source disk offline), IN_PROGRESS (copying), COMPLETE (done), FAILED (error)';
COMMENT ON COLUMN file_migration_history.migration_type IS 'Type of migration: NAS_COPY (source to NAS), WAREHOUSE_COPY (source to warehouse), NAS_TO_WAREHOUSE (NAS to warehouse), WAREHOUSE_TO_NAS (warehouse to NAS)';
