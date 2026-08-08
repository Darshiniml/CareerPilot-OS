package com.careerpilot.backend.modules.agent.domain;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class AgentResult {
    
    public enum Status {
        SUCCESS,
        FAILED,
        UNAVAILABLE,
        UNSUPPORTED_CONNECTOR,
        BLOCKED
    }

    private Status status;
    private String message;
    private Map<String, Object> outputData;
    private Throwable exception;
}
