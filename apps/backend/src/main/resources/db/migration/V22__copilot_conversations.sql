-- Career Copilot: per-user conversation memory and an audit log of every tool call.

CREATE TABLE IF NOT EXISTS copilot_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,          -- USER | ASSISTANT
    content TEXT NOT NULL,
    tools_json TEXT,                    -- tools used for an assistant turn
    citations_json TEXT,
    actions_json TEXT,
    ai_model VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_copilot_messages_user ON copilot_messages(user_id, created_at);

CREATE TABLE IF NOT EXISTS copilot_tool_audit (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    correlation_id VARCHAR(64),
    tool_name VARCHAR(80) NOT NULL,
    arguments_json TEXT,
    selected_by VARCHAR(20) NOT NULL,   -- MODEL | FALLBACK
    success BOOLEAN NOT NULL,
    error_message VARCHAR(500),
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_copilot_tool_audit_user ON copilot_tool_audit(user_id, created_at);
