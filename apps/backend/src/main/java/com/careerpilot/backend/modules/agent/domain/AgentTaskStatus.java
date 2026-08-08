package com.careerpilot.backend.modules.agent.domain;

public enum AgentTaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    SKIPPED,
    BLOCKED
}
