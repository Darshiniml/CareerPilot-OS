-- Autonomous Career Agent Orchestration Schema

CREATE TABLE agent_workflows (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL, -- PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    correlation_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_tasks (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES agent_workflows(id) ON DELETE CASCADE,
    task_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, RUNNING, COMPLETED, FAILED, RETRYING, SKIPPED
    agent_id VARCHAR(100),
    priority INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    payload JSONB,
    result JSONB,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_executions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES agent_tasks(id) ON DELETE CASCADE,
    agent_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL, -- RUNNING, COMPLETED, FAILED
    payload JSONB,
    result JSONB,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE agent_policies (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_applications_per_day INT NOT NULL DEFAULT 5,
    minimum_match_score DOUBLE PRECISION NOT NULL DEFAULT 70.0, -- Matching Engine returns 0-100
    allowed_employment_types VARCHAR(255) NOT NULL DEFAULT 'FULL_TIME,PART_TIME,CONTRACT',
    allowed_locations VARCHAR(500) NOT NULL DEFAULT 'REMOTE,HYBRID,BANGALORE',
    allowed_remote_types VARCHAR(255) NOT NULL DEFAULT 'REMOTE,HYBRID,ON_SITE',
    allowed_companies TEXT,
    blocked_companies TEXT,
    require_approval BOOLEAN NOT NULL DEFAULT TRUE,
    allow_automatic_submission BOOLEAN NOT NULL DEFAULT FALSE,
    allow_reference_research BOOLEAN NOT NULL DEFAULT TRUE,
    allow_external_connectors BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_events (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(255) NOT NULL,
    workflow_id UUID,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_workflows_user ON agent_workflows(user_id);
CREATE INDEX idx_agent_workflows_status ON agent_workflows(status);
CREATE INDEX idx_agent_tasks_workflow ON agent_tasks(workflow_id);
CREATE INDEX idx_agent_tasks_status ON agent_tasks(status);
CREATE INDEX idx_agent_tasks_agent ON agent_tasks(agent_id);
CREATE INDEX idx_agent_workflows_created ON agent_workflows(created_at);
CREATE INDEX idx_agent_workflows_corr ON agent_workflows(correlation_id);
CREATE INDEX idx_agent_policies_user ON agent_policies(user_id);
