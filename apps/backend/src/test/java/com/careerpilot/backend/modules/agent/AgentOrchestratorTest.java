package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.repositories.*;
import com.careerpilot.backend.modules.agent.services.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AgentOrchestratorTest {

    private AgentWorkflowRepository workflowRepository;
    private AgentTaskRepository taskRepository;
    private AgentExecutionRepository executionRepository;
    private AgentExecutionService executionService;
    private AgentRegistry agentRegistry;
    private MeterRegistry meterRegistry;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    public void setUp() {
        workflowRepository = mock(AgentWorkflowRepository.class);
        taskRepository = mock(AgentTaskRepository.class);
        executionRepository = mock(AgentExecutionRepository.class);
        executionService = mock(AgentExecutionService.class);
        agentRegistry = mock(AgentRegistry.class);
        meterRegistry = mock(MeterRegistry.class);
        
        orchestrator = new AgentOrchestrator(
                workflowRepository, taskRepository, executionRepository, 
                executionService, agentRegistry, meterRegistry);
    }

    @Test
    public void testActiveExecutionsResolution() {
        UUID taskId = UUID.randomUUID();
        AgentExecution activeExec = AgentExecution.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .agentId("mock-agent")
                .status(AgentExecutionStatus.RUNNING)
                .build();
                
        when(executionRepository.findByStatus(AgentExecutionStatus.RUNNING)).thenReturn(List.of(activeExec));
        
        AgentTask task = AgentTask.builder()
                .id(taskId)
                .taskType("MOCK_TASK")
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        
        Map<String, Object> result = orchestrator.getActiveExecutions();
        assertNotNull(result);
        assertEquals(1, result.get("activeAgents"));
        
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("executions");
        assertEquals("mock-agent", list.get(0).get("agentId"));
        assertEquals("MOCK_TASK", list.get(0).get("taskType"));
    }
}
