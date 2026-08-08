package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.AgentRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AgentRegistryTest {

    private CareerAgent mockAgent;
    private AgentRegistry registry;

    @BeforeEach
    public void setUp() {
        mockAgent = mock(CareerAgent.class);
        when(mockAgent.getAgentId()).thenReturn("mock-agent");
        when(mockAgent.getSupportedTaskTypes()).thenReturn(List.of("MOCK_TASK"));
        when(mockAgent.getHealthStatus()).thenReturn(AgentStatus.HEALTHY);
        
        registry = new AgentRegistry(List.of(mockAgent));
    }

    @Test
    public void testRegistryDiscovery() {
        Optional<CareerAgent> agent = registry.get("mock-agent");
        assertTrue(agent.isPresent());
        assertEquals("mock-agent", agent.get().getAgentId());
    }

    @Test
    public void testFindByTaskType() {
        Optional<CareerAgent> agent = registry.findByTaskType("MOCK_TASK");
        assertTrue(agent.isPresent());
        assertEquals("mock-agent", agent.get().getAgentId());
    }

    @Test
    public void testEnableDisable() {
        assertTrue(registry.isEnabled("mock-agent"));
        registry.disable("mock-agent");
        assertFalse(registry.isEnabled("mock-agent"));
        
        Optional<CareerAgent> agent = registry.findByTaskType("MOCK_TASK");
        assertFalse(agent.isPresent()); // Disabled agents shouldn't be found
        
        registry.enable("mock-agent");
        assertTrue(registry.isEnabled("mock-agent"));
    }

    @Test
    public void testHealthStatus() {
        Map<String, AgentStatus> health = registry.health();
        assertEquals(AgentStatus.HEALTHY, health.get("mock-agent"));
    }
}
