package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.ApplicationAgent;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.backend.modules.application.services.ApplicationOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ApplicationAgentTest {

    @Test
    public void testApplicationAgentNoResume() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        ConnectorRegistry connectorRegistry = mock(ConnectorRegistry.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        ResumeVersionRepository resumeVersionRepository = mock(ResumeVersionRepository.class);
        ApplicationOrchestratorService applicationOrchestratorService = mock(ApplicationOrchestratorService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        com.careerpilot.backend.modules.application.services.SubmissionPreflightService preflightService = mock(com.careerpilot.backend.modules.application.services.SubmissionPreflightService.class);

        ApplicationAgent agent = new ApplicationAgent(
                jobDiscoveryService, connectorRegistry, resumeRepository, resumeVersionRepository, applicationOrchestratorService, eventPublisher, preflightService,
                mock(com.careerpilot.backend.modules.ai.matching.MatchService.class));

        UUID userId = UUID.randomUUID();
        when(resumeRepository.findDefaultByUserId(userId)).thenReturn(Optional.empty());

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .policy(new AgentPolicy())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("No active resume available"));
    }
}
