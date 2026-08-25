package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.MatchingAgent;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.backend.modules.analytics.services.DataCollector;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.company.repositories.CompanyIntelligenceCacheRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MatchingAgentTest {

    @Test
    public void testMatchingAgentMissingResumeCache() {
        JobDiscoveryService jobDiscoveryService = mock(JobDiscoveryService.class);
        MatchingEngine matchingEngine = mock(MatchingEngine.class);
        DataCollector dataCollector = mock(DataCollector.class);
        JobIntelligenceCacheRepository jobCacheRepository = mock(JobIntelligenceCacheRepository.class);
        CompanyIntelligenceCacheRepository companyCacheRepository = mock(CompanyIntelligenceCacheRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent jobDiscoveryAgent = mock(com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent.class);
        com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService prioritizationService = mock(com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService.class);

        MatchingAgent agent = new MatchingAgent(
                jobDiscoveryService, matchingEngine, dataCollector, jobCacheRepository, companyCacheRepository, eventPublisher, jobDiscoveryAgent, prioritizationService);

        UUID userId = UUID.randomUUID();
        when(dataCollector.collectCandidateData(userId)).thenReturn(new HashMap<>()); // Returns empty candidateData

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .policy(new AgentPolicy())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("Candidate resume has not been analyzed"));
    }
}
