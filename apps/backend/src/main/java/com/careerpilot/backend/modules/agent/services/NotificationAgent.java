package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.PlatformNotification;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import com.careerpilot.shared.events.CareerAutomationCompletedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationAgent implements CareerAgent {

    private final PlatformNotificationRepository notificationRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public NotificationAgent(PlatformNotificationRepository notificationRepository,
                             ApplicationRecordRepository applicationRepository,
                             ApplicationEventPublisher eventPublisher,
                             ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.applicationRepository = applicationRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAgentId() {
        return "notification-agent";
    }

    @Override
    public String getName() {
        return "Career Notification & Alerts Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("PLATFORM_NOTIFICATIONS", "EVENT_DISPATCHING");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("NOTIFICATION");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        
        try {
            UUID applicationId = applicationIdFor(task, userId);
            if (applicationId == null) {
                return AgentResult.builder()
                        .status(AgentResult.Status.FAILED)
                        .message("Notification task requires an application owned by the candidate.")
                        .build();
            }
            // 1. Save Platform Notification record
            PlatformNotification platformNotif = PlatformNotification.builder()
                    .id(UUID.randomUUID())
                    .candidateId(userId)
                    .applicationId(applicationId)
                    .type("PLATFORM")
                    .title("Career Automation Complete")
                    .message("The autonomous career search workflow completed successfully. Evaluated discovery and matched listings.")
                    .read(false)
                    .createdAt(Instant.now())
                    .build();
            notificationRepository.save(platformNotif);
            
            // 2. Publish Completed Event
            eventPublisher.publishEvent(CareerAutomationCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .correlationId(UUID.fromString(context.getCorrelationId()))
                    .workflowId(context.getWorkflowId())
                    .userId(userId)
                    .build());
            
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("notificationId", platformNotif.getId().toString());
            outputData.put("success", true);
            
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Sent platform automation notifications.")
                    .outputData(outputData)
                    .build();
            
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed to dispatch notifications: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }

    private UUID applicationIdFor(AgentTask task, UUID userId) throws Exception {
        if (task.getPayloadJson() == null || task.getPayloadJson().isBlank()) {
            return null;
        }
        JsonNode applicationId = objectMapper.readTree(task.getPayloadJson()).path("applicationId");
        if (applicationId.isMissingNode() || applicationId.asText().isBlank()) {
            return null;
        }
        ApplicationRecord application = applicationRepository.findById(UUID.fromString(applicationId.asText()))
                .orElse(null);
        return application != null && userId.equals(application.getCandidateId())
                ? application.getApplicationId()
                : null;
    }
}
