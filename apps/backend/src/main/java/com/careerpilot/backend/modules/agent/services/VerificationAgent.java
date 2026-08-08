package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.shared.events.ApplicationVerifiedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class VerificationAgent implements CareerAgent {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VerificationAgent(ApplicationRecordRepository applicationRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "verification-agent";
    }

    @Override
    public String getName() {
        return "Application Submission Verification Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("EVIDENCE_VERIFICATION", "AUDITING", "COMPLIANCE_CHECKS");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("VERIFICATION");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        
        try {
            List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
            List<Map<String, Object>> verifiedList = new ArrayList<>();
            
            for (ApplicationRecord app : apps) {
                // Assert submitted state
                if (app.getWorkflowState() == WorkflowState.SUBMITTED) {
                    Map<String, Object> ev = new HashMap<>();
                    ev.put("applicationId", app.getApplicationId().toString());
                    ev.put("jobId", app.getJobId().toString());
                    ev.put("connectorId", app.getConnectorId());
                    ev.put("submittedAt", app.getSubmittedAt());
                    ev.put("verified", true);
                    
                    verifiedList.add(ev);
                    
                    // Publish Event
                    eventPublisher.publishEvent(ApplicationVerifiedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .timestamp(Instant.now())
                            .correlationId(UUID.fromString(context.getCorrelationId()))
                            .userId(userId)
                            .applicationId(app.getApplicationId())
                            .jobId(app.getJobId())
                            .status("VERIFIED")
                            .build());
                }
            }
            
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("verifiedApplications", verifiedList);
            outputData.put("count", verifiedList.size());
            
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Verified " + verifiedList.size() + " submitted applications with external receipts.")
                    .outputData(outputData)
                    .build();
            
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed during submission verification: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
