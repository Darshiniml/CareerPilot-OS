package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.ai.company.services.CompanyIntelligenceService;
import com.careerpilot.shared.events.CompanyResearchCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class CompanyResearchAgent implements CareerAgent {

    private final JobDiscoveryService jobDiscoveryService;
    private final CompanyIntelligenceService companyIntelligenceService;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyResearchAgent(JobDiscoveryService jobDiscoveryService,
                                CompanyIntelligenceService companyIntelligenceService,
                                ApplicationEventPublisher eventPublisher) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.companyIntelligenceService = companyIntelligenceService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "company-research-agent";
    }

    @Override
    public String getName() {
        return "Company Insights & Website Research Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("CRAWLING", "HIRING_SIGNALS_DETECTION", "METADATA_ANALYSIS");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("COMPANY_RESEARCH", "RESEARCH_COMPANIES");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        
        try {
            // 1. Gather discovered jobs
            List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
            Set<String> uniqueCompanies = new HashSet<>();
            for (DiscoveryJob job : jobs) {
                if (job.getCompany() != null && !job.getCompany().isBlank()) {
                    uniqueCompanies.add(job.getCompany().trim());
                }
            }
            
            // 2. Perform URL research for each company
            Map<String, String> companyUrls = new HashMap<>();
            for (String companyName : uniqueCompanies) {
                String cleanName = companyName.toLowerCase().replaceAll("[^a-z0-9]", "");
                String companyUrl = "http://www." + cleanName + ".com";
                
                try {
                    companyIntelligenceService.processCompanyUrl(userId, companyUrl);
                    companyUrls.put(companyName, companyUrl);
                } catch (Exception ex) {
                    // Fail gracefully on individual company failures
                    companyUrls.put(companyName, "RESEARCH_FAILED: " + ex.getMessage());
                }
            }
            
            // 3. Publish Event
            eventPublisher.publishEvent(CompanyResearchCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .correlationId(UUID.fromString(context.getCorrelationId()))
                    .workflowId(context.getWorkflowId())
                    .userId(userId)
                    .build());
            
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("researchedCompanies", companyUrls);
            outputData.put("researchedCount", companyUrls.size());
            
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Researched " + companyUrls.size() + " company profiles.")
                    .outputData(outputData)
                    .build();
            
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed to perform company research: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
