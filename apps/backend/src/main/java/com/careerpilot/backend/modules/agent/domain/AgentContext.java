package com.careerpilot.backend.modules.agent.domain;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AgentContext {
    private UUID userId;
    private UUID workflowId;
    private String correlationId;
    private AgentPolicy policy;
    private Map<String, Object> globalData;
}
