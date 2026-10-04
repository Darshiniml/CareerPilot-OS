-- Milestone 22.4: Application timeline intelligence (communication-derived, provenance-preserving)

CREATE TABLE IF NOT EXISTS application_timeline_events (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    communication_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    previous_state VARCHAR(50),
    new_state VARCHAR(50),
    state_changed BOOLEAN NOT NULL DEFAULT FALSE,
    classification VARCHAR(50) NOT NULL,
    classification_confidence DOUBLE PRECISION,
    evidence VARCHAR(2000),
    event_timestamp TIMESTAMP NOT NULL,
    source VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_timeline_app_comm_event UNIQUE (application_id, communication_id, event_type),
    CONSTRAINT fk_timeline_application FOREIGN KEY (application_id) REFERENCES application_records(application_id),
    CONSTRAINT fk_timeline_communication FOREIGN KEY (communication_id) REFERENCES hr_communications(id)
);

CREATE INDEX IF NOT EXISTS idx_timeline_app_time ON application_timeline_events(application_id, event_timestamp);
