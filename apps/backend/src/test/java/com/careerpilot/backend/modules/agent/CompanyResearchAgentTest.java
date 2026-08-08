package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.CompanyResearchAgent;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.ai.company.services.CompanyIntelligenceService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CompanyResearchAgentTest {

    @Test
    public void testCompanyResearchAgentSuccess() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        CompanyIntelligenceService companyIntelligenceService = mock(CompanyIntelligenceService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        CompanyResearchAgent agent = new CompanyResearchAgent(
                jobDiscoveryService, companyIntelligenceService, eventPublisher);

        UUID userId = UUID.randomUUID();
        when(jobDiscoveryService.jobs()).thenReturn(List.of());

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
    }
}
