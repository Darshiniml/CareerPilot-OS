package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.TrackingAgent;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TrackingAgentTest {

    @Test
    public void testTrackingAgentSuccess() {
        ApplicationRecordRepository applicationRepository = mock(ApplicationRecordRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        com.careerpilot.backend.modules.analytics.services.CareerAnalyticsService analyticsService = mock(com.careerpilot.backend.modules.analytics.services.CareerAnalyticsService.class);

        TrackingAgent agent = new TrackingAgent(applicationRepository, eventPublisher, analyticsService);

        UUID userId = UUID.randomUUID();
        when(applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
    }
}
