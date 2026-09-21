-- Milestone 22: HR Communication Intelligence & Application Follow-up (foundation schema)

CREATE TABLE IF NOT EXISTS hr_communications (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    external_message_id VARCHAR(255) NOT NULL,
    thread_id VARCHAR(255),
    sender VARCHAR(320) NOT NULL,
    recipient VARCHAR(320),
    subject VARCHAR(1000),
    body TEXT,
    received_at TIMESTAMP NOT NULL,
    matched_application_id UUID,
    match_confidence DOUBLE PRECISION,
    match_evidence VARCHAR(2000),
    classification VARCHAR(50) NOT NULL,
    classification_confidence DOUBLE PRECISION,
    classification_reason VARCHAR(2000),
    processing_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_hr_comm_provider_external UNIQUE (provider, external_message_id)
);

CREATE INDEX IF NOT EXISTS idx_hr_comm_candidate_received ON hr_communications(candidate_id, received_at);
CREATE INDEX IF NOT EXISTS idx_hr_comm_matched_application ON hr_communications(matched_application_id);
CREATE INDEX IF NOT EXISTS idx_hr_comm_external_message ON hr_communications(external_message_id);
CREATE INDEX IF NOT EXISTS idx_hr_comm_thread ON hr_communications(thread_id);
CREATE INDEX IF NOT EXISTS idx_hr_comm_received_at ON hr_communications(received_at);
CREATE INDEX IF NOT EXISTS idx_hr_comm_processing_status ON hr_communications(processing_status);
