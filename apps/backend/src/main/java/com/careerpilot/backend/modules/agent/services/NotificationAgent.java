package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.PlatformNotification;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import com.careerpilot.shared.events.CareerAutomationCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationAgent implements CareerAgent {

    private final PlatformNotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationAgent(PlatformNotificationRepository notificationRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
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
            // 1. Save Platform Notification record
            PlatformNotification platformNotif = PlatformNotification.builder()
                    .id(UUID.randomUUID())
                    .candidateId(userId)
                    .applicationId(UUID.randomUUID()) // dummy reference
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
}
