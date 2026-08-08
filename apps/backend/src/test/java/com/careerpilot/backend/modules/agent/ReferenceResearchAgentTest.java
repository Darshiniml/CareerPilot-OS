package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.ReferenceResearchAgent;
import com.careerpilot.backend.modules.agent.services.ReferenceSourceProvider;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ReferenceResearchAgentTest {

    @Test
    public void testReferenceAgentPolicyDisabled() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReferenceSourceProvider mockProvider = mock(ReferenceSourceProvider.class);

        ReferenceResearchAgent agent = new ReferenceResearchAgent(
                jobDiscoveryService, List.of(mockProvider), eventPublisher);

        UUID userId = UUID.randomUUID();
        AgentPolicy policy = AgentPolicy.builder()
                .allowReferenceResearch(false) // Disable reference research
                .build();

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .policy(policy)
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.getMessage().contains("skipped per user policy settings"));
    }
}
