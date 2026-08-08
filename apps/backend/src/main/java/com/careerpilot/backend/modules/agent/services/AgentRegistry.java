package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.CareerAgent;
import com.careerpilot.backend.modules.agent.domain.AgentStatus;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentRegistry {

    private final Map<String, CareerAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, Boolean> enabledStatus = new ConcurrentHashMap<>();

    public AgentRegistry(List<CareerAgent> springManagedAgents) {
        if (springManagedAgents != null) {
            for (CareerAgent agent : springManagedAgents) {
                register(agent);
            }
        }
    }

    public void register(CareerAgent agent) {
        agents.put(agent.getAgentId(), agent);
        enabledStatus.putIfAbsent(agent.getAgentId(), true);
    }

    public void unregister(String agentId) {
        agents.remove(agentId);
        enabledStatus.remove(agentId);
    }

    public Optional<CareerAgent> get(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public Optional<CareerAgent> findByTaskType(String taskType) {
        return agents.values().stream()
                .filter(agent -> enabledStatus.getOrDefault(agent.getAgentId(), false))
                .filter(agent -> agent.getSupportedTaskTypes().contains(taskType))
                .findFirst();
    }

    public Collection<CareerAgent> list() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public boolean isEnabled(String agentId) {
        return enabledStatus.getOrDefault(agentId, false);
    }

    public void enable(String agentId) {
        if (agents.containsKey(agentId)) {
            enabledStatus.put(agentId, true);
        }
    }

    public void disable(String agentId) {
        if (agents.containsKey(agentId)) {
            enabledStatus.put(agentId, false);
        }
    }

    public Map<String, AgentStatus> health() {
        Map<String, AgentStatus> statusMap = new HashMap<>();
        for (CareerAgent agent : agents.values()) {
            statusMap.put(agent.getAgentId(), agent.getHealthStatus());
        }
        return statusMap;
    }
}
