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
        com.careerpilot.backend.modules.ai.matching.MatchService matchService = mock(com.careerpilot.backend.modules.ai.matching.MatchService.class);
        com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService candidateKnowledgeService = mock(com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent jobDiscoveryAgent = mock(com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent.class);
        com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService prioritizationService = mock(com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService.class);

        MatchingAgent agent = new MatchingAgent(
                jobDiscoveryService, matchService, candidateKnowledgeService, eventPublisher, jobDiscoveryAgent, prioritizationService);

        UUID userId = UUID.randomUUID();
        when(candidateKnowledgeService.primaryResume(userId)).thenReturn(java.util.Optional.empty()); // no processed resume

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .policy(new AgentPolicy())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("Candidate resume has not been analyzed"));
        verifyNoInteractions(matchService); // nothing is scored against empty candidate data
    }
}
