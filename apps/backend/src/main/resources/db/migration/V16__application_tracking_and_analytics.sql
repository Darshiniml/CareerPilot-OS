-- Milestone 19: Application Tracking & Verification Tables

CREATE TABLE IF NOT EXISTS application_verifications (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    evidence_reference TEXT,
    confirmation_id VARCHAR(255),
    reason TEXT,
    verified_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_verifications_app_id ON application_verifications(application_id);
CREATE INDEX IF NOT EXISTS idx_app_notif_candidate ON application_notifications(candidate_id, is_read, created_at);
CREATE INDEX IF NOT EXISTS idx_app_history_app_id ON application_state_history(application_id, to_state, created_at);
