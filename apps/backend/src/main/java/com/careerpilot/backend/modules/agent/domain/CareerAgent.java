package com.careerpilot.backend.modules.agent.domain;

import java.util.List;

public interface CareerAgent {
    String getAgentId();
    String getName();
    String getVersion();
    List<String> getCapabilities();
    List<String> getSupportedTaskTypes();
    AgentResult execute(AgentContext context, AgentTask task);
    AgentStatus getHealthStatus();
}
