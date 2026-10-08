package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.CompanyResearchAgent;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CompanyResearchAgentTest {

    @Test
    public void researchUsesOnlyThePostingTextOfTheUsersApplications() {
        ApplicationRecordRepository applications = mock(ApplicationRecordRepository.class);
        DiscoveryJobRepository jobs = mock(DiscoveryJobRepository.class);
        JobContextService jobContext = mock(JobContextService.class);
        AiGatewayClient gateway = mock(AiGatewayClient.class);
        CompanyResearchAgent agent = new CompanyResearchAgent(applications, jobs, jobContext, gateway,
                mock(ApplicationEventPublisher.class));

        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        DiscoveryJob job = DiscoveryJob.builder().id(jobId).company("Acme").title("Engineer").connectorId("greenhouse").build();
        when(applications.findByCandidateIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                ApplicationRecord.builder().applicationId(UUID.randomUUID()).candidateId(userId).jobId(jobId).build()));
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
        when(jobContext.jobText(job)).thenReturn("Acme builds payment APIs.");
        when(gateway.run(eq("COMPANY_RESEARCH_SUMMARY"), anyMap())).thenReturn(Map.of("summary", "Payments company"));

        AgentResult result = agent.execute(AgentContext.builder().userId(userId).build(), AgentTask.builder().build());

        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
        verify(gateway).run(eq("COMPANY_RESEARCH_SUMMARY"), argThat(p ->
                p.get("sources").toString().contains("Acme builds payment APIs.")));
    }

    @Test
    public void noApplicationsMeansNothingIsResearched() {
        ApplicationRecordRepository applications = mock(ApplicationRecordRepository.class);
        AiGatewayClient gateway = mock(AiGatewayClient.class);
        CompanyResearchAgent agent = new CompanyResearchAgent(applications, mock(DiscoveryJobRepository.class),
                mock(JobContextService.class), gateway, mock(ApplicationEventPublisher.class));
        UUID userId = UUID.randomUUID();
        when(applications.findByCandidateIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        AgentResult result = agent.execute(AgentContext.builder().userId(userId).build(), AgentTask.builder().build());

        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.getMessage().contains("No applications"));
        verifyNoInteractions(gateway);
    }
}
