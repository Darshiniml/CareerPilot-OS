package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.events.CompanyResearchCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Researches the companies behind the candidate's own applications using only real sources (the
 * discovered job posting text). It never guesses company websites. Bounded to a few companies per
 * run because each summary is a model call.
 */
@Service
public class CompanyResearchAgent implements CareerAgent {

    static final int MAX_COMPANIES_PER_RUN = 3;

    private final ApplicationRecordRepository applicationRepository;
    private final DiscoveryJobRepository jobRepository;
    private final JobContextService jobContextService;
    private final AiGatewayClient gatewayClient;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyResearchAgent(ApplicationRecordRepository applicationRepository,
                                DiscoveryJobRepository jobRepository,
                                JobContextService jobContextService,
                                AiGatewayClient gatewayClient,
                                ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.jobContextService = jobContextService;
        this.gatewayClient = gatewayClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "company-research-agent";
    }

    @Override
    public String getName() {
        return "Company Research Agent";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("COMPANY_RESEARCH_FROM_POSTINGS");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("COMPANY_RESEARCH", "RESEARCH_COMPANIES");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        Map<String, Object> researched = new LinkedHashMap<>();
        List<Map<String, String>> failures = new ArrayList<>();
        Set<String> seenCompanies = new HashSet<>();
        for (ApplicationRecord app : applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)) {
            if (researched.size() + failures.size() >= MAX_COMPANIES_PER_RUN) {
                break;
            }
            Optional<DiscoveryJob> job = jobRepository.findById(app.getJobId());
            if (job.isEmpty() || job.get().getCompany() == null || !seenCompanies.add(job.get().getCompany().toLowerCase(Locale.ROOT))) {
                continue;
            }
            DiscoveryJob j = job.get();
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("sources", List.of(Map.of("source", "job-posting:" + j.getConnectorId(), "text", jobContextService.jobText(j))));
                payload.put("context", Map.of("company", j.getCompany(), "role", Objects.toString(j.getTitle(), "")));
                researched.put(j.getCompany(), gatewayClient.run("COMPANY_RESEARCH_SUMMARY", payload));
            } catch (AiServiceException e) {
                failures.add(Map.of("company", j.getCompany(), "error", e.getCode()));
            }
        }
        eventPublisher.publishEvent(CompanyResearchCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .correlationId(context.getCorrelationId() != null ? UUID.fromString(context.getCorrelationId()) : null)
                .workflowId(context.getWorkflowId())
                .userId(userId)
                .build());
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("researchedCompanies", researched);
        outputData.put("failures", failures);
        AgentResult.Status status = failures.isEmpty() ? AgentResult.Status.SUCCESS
                : researched.isEmpty() ? AgentResult.Status.UNAVAILABLE : AgentResult.Status.SUCCESS;
        String message = researched.isEmpty() && failures.isEmpty()
                ? "No applications to research yet."
                : "Researched " + researched.size() + " companies from their job postings"
                + (failures.isEmpty() ? "." : "; " + failures.size() + " failed (AI unavailable).");
        return AgentResult.builder().status(status).message(message).outputData(outputData).build();
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
