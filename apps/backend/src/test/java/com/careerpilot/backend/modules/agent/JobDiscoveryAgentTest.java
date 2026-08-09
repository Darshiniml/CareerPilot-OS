package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.profile.repositories.*;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.connector.sdk.Connector;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JobDiscoveryAgentTest {

    @Test
    public void testJobDiscoveryAgentWithNoConnectorsBlocked() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        ConnectorRegistry connectorRegistry = mock(ConnectorRegistry.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        UserPreferredRoleRepository preferredRoleRepository = mock(UserPreferredRoleRepository.class);
        UserPreferredLocationRepository preferredLocationRepository = mock(UserPreferredLocationRepository.class);
        ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);

        when(connectorRegistry.enabled()).thenReturn(List.of());

        JobDiscoveryAgent agent = new JobDiscoveryAgent(
                jobDiscoveryService,
                connectorRegistry,
                eventPublisher,
                preferredRoleRepository,
                preferredLocationRepository,
                experienceRepository,
                projectRepository,
                resumeRepository
        );

        UUID userId = UUID.randomUUID();
        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.BLOCKED, result.getStatus());
    }

    @Test
    public void testJobDiscoveryAgentSuccessWithEnabledConnectors() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        ConnectorRegistry connectorRegistry = mock(ConnectorRegistry.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        UserPreferredRoleRepository preferredRoleRepository = mock(UserPreferredRoleRepository.class);
        UserPreferredLocationRepository preferredLocationRepository = mock(UserPreferredLocationRepository.class);
        ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ResumeRepository resumeRepository = mock(ResumeRepository.class);

        Connector mockConnector = mock(Connector.class);
        when(connectorRegistry.enabled()).thenReturn(List.of(mockConnector));

        JobDiscoveryAgent agent = new JobDiscoveryAgent(
                jobDiscoveryService,
                connectorRegistry,
                eventPublisher,
                preferredRoleRepository,
                preferredLocationRepository,
                experienceRepository,
                projectRepository,
                resumeRepository
        );

        UUID userId = UUID.randomUUID();
        when(jobDiscoveryService.jobs()).thenReturn(List.of());

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertNotNull(result);
    }
}
