package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.shared.events.ReferencesResearchCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class ReferenceResearchAgent implements CareerAgent {

    private final JobDiscoveryService jobDiscoveryService;
    private final List<ReferenceSourceProvider> providers;
    private final ApplicationEventPublisher eventPublisher;

    public ReferenceResearchAgent(JobDiscoveryService jobDiscoveryService,
                                  List<ReferenceSourceProvider> providers,
                                  ApplicationEventPublisher eventPublisher) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.providers = providers;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "reference-research-agent";
    }

    @Override
    public String getName() {
        return "Public Signals & References Verification Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("PUBLIC_SIGNALS_VERIFICATION", "COMPLIANCE_ENFORCEMENT");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("REFERENCE_RESEARCH");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        AgentPolicy policy = context.getPolicy();
        
        // 1. Check if policy allows reference research
        if (policy != null && !policy.getAllowReferenceResearch()) {
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Reference research skipped per user policy settings.")
                    .build();
        }
        
        try {
            List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
            Map<String, Object> allSignals = new HashMap<>();
            boolean anyProviderSuccess = false;
            
            for (DiscoveryJob job : jobs) {
                if (job.getCompany() == null || job.getCompany().isBlank()) {
                    continue;
                }
                
                List<Map<String, Object>> companySignalsList = new ArrayList<>();
                for (ReferenceSourceProvider provider : providers) {
                    try {
                        Map<String, Object> signals = provider.queryPublicSignals(userId, job.getCompany());
                        companySignalsList.add(signals);
                        anyProviderSuccess = true;
                    } catch (Exception ex) {
                        // Log provider exclusion and continue
                        Map<String, Object> errorSig = new HashMap<>();
                        errorSig.put("provider", provider.getProviderName());
                        errorSig.put("status", "UNAVAILABLE");
                        errorSig.put("reason", ex.getMessage());
                        companySignalsList.add(errorSig);
                    }
                }
                allSignals.put(job.getCompany(), companySignalsList);
            }
            
            // 2. Publish Event
            eventPublisher.publishEvent(ReferencesResearchCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .correlationId(UUID.fromString(context.getCorrelationId()))
                    .workflowId(context.getWorkflowId())
                    .userId(userId)
                    .build());
            
            if (!anyProviderSuccess && !providers.isEmpty()) {
                // If all public sources are unavailable or blocked, return UNAVAILABLE status to indicate partial success
                return AgentResult.builder()
                        .status(AgentResult.Status.UNAVAILABLE)
                        .message("Public signals are currently unavailable due to scraping blocks or CAPTCHA requirements.")
                        .outputData(allSignals)
                        .build();
            }
            
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Public hiring signals verified successfully.")
                    .outputData(allSignals)
                    .build();
            
        } catch (Exception e) {
            // Keep the complete workflow running
            return AgentResult.builder()
                    .status(AgentResult.Status.UNAVAILABLE)
                    .message("Failed to process reference signals: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
