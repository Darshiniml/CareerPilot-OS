package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.AgentContext;
import com.careerpilot.backend.modules.agent.domain.AgentTask;
import com.careerpilot.backend.modules.agent.domain.AgentTaskStatus;
import com.careerpilot.shared.events.UserLoggedInEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

@Component
@Slf4j
public class UserSessionListener {

    private final JobDiscoveryAgent jobDiscoveryAgent;

    public UserSessionListener(JobDiscoveryAgent jobDiscoveryAgent) {
        this.jobDiscoveryAgent = jobDiscoveryAgent;
    }

    @Async
    @EventListener
    public void handleUserLoggedIn(UserLoggedInEvent event) {
        log.info("[AUTO-DISCOVERY-LOGIN] User logged in: userId={}, email={}. Launching background job discovery.",
                event.getUserId(), event.getEmail());

        try {
            AgentContext context = AgentContext.builder()
                    .userId(event.getUserId())
                    .workflowId(UUID.randomUUID())
                    .correlationId(UUID.randomUUID().toString())
                    .globalData(new HashMap<>())
                    .build();

            AgentTask task = AgentTask.builder()
                    .id(UUID.randomUUID())
                    .workflowId(context.getWorkflowId())
                    .taskType("JOB_DISCOVERY")
                    .status(AgentTaskStatus.RUNNING)
                    .priority(10)
                    .retryCount(0)
                    .createdAt(Instant.now())
                    .build();

            jobDiscoveryAgent.execute(context, task);
            log.info("[AUTO-DISCOVERY-LOGIN] Background job discovery completed successfully for userId={}", event.getUserId());
        } catch (Exception e) {
            log.warn("[AUTO-DISCOVERY-LOGIN] Background job discovery failed for userId={}: {}", event.getUserId(), e.getMessage());
        }
    }
}
