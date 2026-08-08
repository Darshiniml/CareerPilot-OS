package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JobDiscoveryAgentTest {

    @Test
    public void testJobDiscoveryAgentSuccess() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        JobDiscoveryAgent agent = new JobDiscoveryAgent(jobDiscoveryService, eventPublisher);

        UUID userId = UUID.randomUUID();
        when(jobDiscoveryService.jobs()).thenReturn(List.of());

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
        verify(jobDiscoveryService, times(1)).discoverAll();
    }
}
