package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.AgentPolicy;
import com.careerpilot.backend.modules.agent.repositories.AgentPolicyRepository;
import com.careerpilot.backend.modules.agent.repositories.AgentTaskRepository;
import com.careerpilot.backend.modules.agent.repositories.AgentWorkflowRepository;
import com.careerpilot.backend.modules.agent.services.AgentExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AgentPolicyTest {

    @Test
    public void testPolicySafetyDefaults() {
        AgentPolicyRepository policyRepository = mock(AgentPolicyRepository.class);
        AgentWorkflowRepository workflowRepository = mock(AgentWorkflowRepository.class);
        AgentTaskRepository taskRepository = mock(AgentTaskRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        
        AgentExecutionService service = new AgentExecutionService(
                workflowRepository, taskRepository, policyRepository, eventPublisher);
                
        UUID userId = UUID.randomUUID();
        when(policyRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(policyRepository.save(any(AgentPolicy.class))).thenAnswer(i -> i.getArguments()[0]);
        
        AgentPolicy policy = service.getOrCreatePolicy(userId);
        assertNotNull(policy);
        assertTrue(policy.getEnabled());
        assertFalse(policy.getAllowAutomaticSubmission()); // Safe default MUST be false
        assertTrue(policy.getRequireApproval());
        assertEquals(70.0, policy.getMinimumMatchScore());
    }
}
