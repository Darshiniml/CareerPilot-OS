-- V16 tried to create idx_app_history_app_id on application_state_history, but V3 already uses that
-- name for application_history, so IF NOT EXISTS silently skipped it. Create it under its own name.
CREATE INDEX IF NOT EXISTS idx_app_state_history_state ON application_state_history(application_id, to_state, created_at);
