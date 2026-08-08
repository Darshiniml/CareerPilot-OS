package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.shared.events.ApplicationStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class TrackingAgent implements CareerAgent {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TrackingAgent(ApplicationRecordRepository applicationRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "tracking-agent";
    }

    @Override
    public String getName() {
        return "Application Progress Tracking Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("STATUS_SYNCHRONIZATION", "INTERVIEW_SCHEDULING_MONITORING");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("TRACKING");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        
        try {
            List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
            List<Map<String, Object>> statusList = new ArrayList<>();
            
            for (ApplicationRecord app : apps) {
                // Fetch external status (Mocked as the current state)
                String currentStatus = app.getWorkflowState().name();
                
                Map<String, Object> item = new HashMap<>();
                item.put("applicationId", app.getApplicationId().toString());
                item.put("jobId", app.getJobId().toString());
                item.put("status", currentStatus);
                statusList.add(item);
                
                // Publish Event
                eventPublisher.publishEvent(ApplicationStatusChangedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .correlationId(UUID.fromString(context.getCorrelationId()))
                        .workflowId(context.getWorkflowId())
                        .userId(userId)
                        .applicationId(app.getApplicationId())
                        .newStatus(currentStatus)
                        .build());
            }
            
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("trackedApplications", statusList);
            outputData.put("count", statusList.size());
            
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Application status tracking synchronized. Monitored " + statusList.size() + " active runs.")
                    .outputData(outputData)
                    .build();
            
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed to track applications: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
