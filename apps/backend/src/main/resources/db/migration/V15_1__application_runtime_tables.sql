-- Application runtime tables (M16-M19). These entities were previously only created by
-- Hibernate ddl-auto in tests; this migration makes the Flyway chain complete on PostgreSQL.
-- Must run before V16 (indexes) and V18 (FK to application_records).

CREATE TABLE IF NOT EXISTS connector_configurations (
    connector_id VARCHAR(64) PRIMARY KEY,
    connector_name VARCHAR(255) NOT NULL,
    connector_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL,
    health_status VARCHAR(50),
    last_error VARCHAR(1000),
    last_success TIMESTAMP(6) WITH TIME ZONE,
    last_synchronization TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS application_records (
    application_id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    company_id UUID,
    job_id UUID NOT NULL,
    connector_id VARCHAR(255),
    workflow_state VARCHAR(255) NOT NULL,
    selected_resume_id UUID,
    selected_resume_version INTEGER,
    submission_method VARCHAR(255),
    match_score DOUBLE PRECISION,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP(6) WITH TIME ZONE,
    last_verified_at TIMESTAMP(6) WITH TIME ZONE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000),
    external_application_id VARCHAR(255),
    metadata TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_application_candidate_job UNIQUE (candidate_id, job_id)
);
CREATE INDEX IF NOT EXISTS idx_app_records_candidate ON application_records(candidate_id, workflow_state);

CREATE TABLE IF NOT EXISTS application_state_history (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    from_state VARCHAR(255),
    to_state VARCHAR(255) NOT NULL,
    reason VARCHAR(255),
    actor_id UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS application_notifications (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    application_id UUID NOT NULL,
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS application_audit (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    user_id UUID,
    action VARCHAR(255) NOT NULL,
    from_state VARCHAR(255),
    to_state VARCHAR(255),
    reason VARCHAR(255),
    ip_address VARCHAR(255),
    correlation_id VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_app_audit_app ON application_audit(application_id, created_at);

CREATE TABLE IF NOT EXISTS application_approval_policies (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL UNIQUE,
    mode VARCHAR(255) NOT NULL,
    minimum_score DOUBLE PRECISION,
    require_remote BOOLEAN,
    technology VARCHAR(255),
    location VARCHAR(255),
    rules TEXT,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS application_retry_history (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    reason VARCHAR(255),
    permanent_failure BOOLEAN NOT NULL,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_retry_app_attempt UNIQUE (application_id, attempt_number)
);
