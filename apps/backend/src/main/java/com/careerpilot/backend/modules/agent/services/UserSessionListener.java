package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.AgentContext;
import com.careerpilot.backend.modules.agent.domain.AgentTask;
import com.careerpilot.backend.modules.agent.domain.AgentTaskStatus;
import com.careerpilot.shared.events.UserLoggedInEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class UserSessionListener {

    private final JobDiscoveryAgent jobDiscoveryAgent;
    private final boolean discoveryEnabled;
    private final Duration minInterval;
    private final AtomicReference<Instant> lastRefresh = new AtomicReference<>(Instant.EPOCH);

    public UserSessionListener(JobDiscoveryAgent jobDiscoveryAgent,
                               @Value("${careerpilot.discovery.enabled:true}") boolean discoveryEnabled,
                               @Value("${careerpilot.discovery.login-refresh-min-interval-ms:1800000}") long minIntervalMs) {
        this.jobDiscoveryAgent = jobDiscoveryAgent;
        this.discoveryEnabled = discoveryEnabled;
        this.minInterval = Duration.ofMillis(minIntervalMs);
    }

    @Async
    @EventListener
    public void handleUserLoggedIn(UserLoggedInEvent event) {
        if (!discoveryEnabled) {
            return;
        }
        Instant now = Instant.now();
        Instant previous = lastRefresh.get();
        if (now.isBefore(previous.plus(minInterval)) || !lastRefresh.compareAndSet(previous, now)) {
            log.debug("[AUTO-DISCOVERY-LOGIN] Skipping refresh for userId={}: jobs were refreshed recently", event.getUserId());
            return;
        }
        log.info("[AUTO-DISCOVERY-LOGIN] User logged in: userId={}. Launching background job discovery.",
                event.getUserId());

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
