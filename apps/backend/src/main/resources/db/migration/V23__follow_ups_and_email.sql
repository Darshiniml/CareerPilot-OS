-- M22.5 follow-up recommendations, M22.6 AI drafts, M22.7 controlled sending.

-- User decisions on deterministic recommendations (dismiss / snooze / done). Recommendations
-- themselves are recomputed from authoritative data on every read.
CREATE TABLE IF NOT EXISTS follow_up_decisions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    recommendation_key VARCHAR(200) NOT NULL,
    application_id UUID NOT NULL,
    decision VARCHAR(20) NOT NULL,            -- DISMISSED | SNOOZED | DONE
    snooze_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_follow_up_decision UNIQUE (user_id, recommendation_key)
);

-- AI-generated drafts. A draft is never sent automatically.
CREATE TABLE IF NOT EXISTS follow_up_drafts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    application_id UUID NOT NULL,
    communication_id UUID,
    recommendation_key VARCHAR(200),
    draft_type VARCHAR(50) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    placeholders_json TEXT,
    verification_json TEXT,
    edited_by_user BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,              -- DRAFT | APPROVED | SENDING | SENT | FAILED | DISCARDED
    recipient VARCHAR(320),
    ai_model VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    provider VARCHAR(20),
    provider_message_id VARCHAR(255),
    send_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_follow_up_drafts_user ON follow_up_drafts(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_follow_up_drafts_app ON follow_up_drafts(application_id);

-- OAuth mailbox connections. Tokens are stored encrypted (AES-GCM, key from the environment).
CREATE TABLE IF NOT EXISTS email_connections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,            -- GMAIL | OUTLOOK
    email_address VARCHAR(320),
    encrypted_refresh_token TEXT NOT NULL,
    scopes VARCHAR(1000),
    status VARCHAR(20) NOT NULL,              -- CONNECTED | REVOKED | ERROR
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_email_connection UNIQUE (user_id, provider)
);

-- Append-only audit trail for approvals and send attempts.
CREATE TABLE IF NOT EXISTS email_send_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    draft_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,          -- APPROVED | SEND_REQUESTED | SENT | FAILED | DISCARDED
    provider VARCHAR(20),
    recipient VARCHAR(320),
    detail VARCHAR(1000),
    correlation_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_email_send_events_draft ON email_send_events(draft_id, created_at);
