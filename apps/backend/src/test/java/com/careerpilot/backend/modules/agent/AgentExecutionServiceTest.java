package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.repositories.*;
import com.careerpilot.backend.modules.agent.services.AgentExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AgentExecutionServiceTest {

    private AgentWorkflowRepository workflowRepository;
    private AgentTaskRepository taskRepository;
    private AgentPolicyRepository policyRepository;
    private ApplicationEventPublisher eventPublisher;
    private AgentExecutionService service;

    @BeforeEach
    public void setUp() {
        workflowRepository = mock(AgentWorkflowRepository.class);
        taskRepository = mock(AgentTaskRepository.class);
        policyRepository = mock(AgentPolicyRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        
        service = new AgentExecutionService(workflowRepository, taskRepository, policyRepository, eventPublisher);
    }

    @Test
    public void testStartWorkflow() {
        UUID userId = UUID.randomUUID();
        when(workflowRepository.save(any(AgentWorkflow.class))).thenAnswer(i -> i.getArguments()[0]);
        
        AgentWorkflow workflow = service.startWorkflow(userId);
        assertNotNull(workflow);
        assertEquals(userId, workflow.getUserId());
        assertEquals(AgentWorkflowStatus.RUNNING, workflow.getStatus());
        verify(taskRepository, times(11)).save(any(AgentTask.class));
    }

    @Test
    public void testPauseResumeCancel() {
        UUID workflowId = UUID.randomUUID();
        AgentWorkflow workflow = AgentWorkflow.builder()
                .id(workflowId)
                .userId(UUID.randomUUID())
                .status(AgentWorkflowStatus.RUNNING)
                .build();
                
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        
        service.pauseWorkflow(workflowId);
        assertEquals(AgentWorkflowStatus.PAUSED, workflow.getStatus());
        
        service.resumeWorkflow(workflowId);
        assertEquals(AgentWorkflowStatus.RUNNING, workflow.getStatus());
        
        when(taskRepository.findByWorkflowId(workflowId)).thenReturn(List.of());
        service.cancelWorkflow(workflowId);
        assertEquals(AgentWorkflowStatus.CANCELLED, workflow.getStatus());
    }
}
