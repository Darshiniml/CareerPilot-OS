package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.analytics.services.CareerAnalyticsService;
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
    private final CareerAnalyticsService analyticsService;

    public TrackingAgent(ApplicationRecordRepository applicationRepository,
                         ApplicationEventPublisher eventPublisher,
                         CareerAnalyticsService analyticsService) {
        this.applicationRepository = applicationRepository;
        this.eventPublisher = eventPublisher;
        this.analyticsService = analyticsService;
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
        return List.of("STATUS_SYNCHRONIZATION", "INTERVIEW_SCHEDULING_MONITORING", "ANALYTICS_UPDATES");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("TRACKING", "TRACK_APPLICATION", "UPDATE_ANALYTICS");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        String type = task.getTaskType();
        
        try {
            if ("UPDATE_ANALYTICS".equals(type)) {
                analyticsService.calculateAndPersistAnalytics(userId, "MONTHLY");
                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Persisted and updated career progression analytics metrics.")
                        .build();
            }

            List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
            List<Map<String, Object>> statusList = new ArrayList<>();
            
            for (ApplicationRecord app : apps) {
                String currentStatus = app.getWorkflowState().name();
                
                Map<String, Object> item = new HashMap<>();
                item.put("applicationId", app.getApplicationId().toString());
                item.put("jobId", app.getJobId().toString());
                item.put("status", currentStatus);
                statusList.add(item);
                
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
                    .message("Failed executing tracking task " + type + ": " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
