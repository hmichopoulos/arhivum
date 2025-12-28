-- Migrate GIT project type to GENERIC
-- Issue #33: GIT removed as a project type, existing GIT projects should become GENERIC

UPDATE code_project
SET project_type = 'GENERIC'
WHERE project_type = 'GIT';
