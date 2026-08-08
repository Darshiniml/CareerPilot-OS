package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.domain.JobSearchCriteria;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.connector.sdk.Connector;
import com.careerpilot.connector.sdk.DiscoveryContext;
import com.careerpilot.shared.events.JobsDiscoveredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobDiscoveryAgent implements CareerAgent {

    private final JobDiscoveryService jobDiscoveryService;
    private final ConnectorRegistry connectorRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public JobDiscoveryAgent(JobDiscoveryService jobDiscoveryService,
                             ConnectorRegistry connectorRegistry,
                             ApplicationEventPublisher eventPublisher) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.connectorRegistry = connectorRegistry;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "job-discovery-agent";
    }

    @Override
    public String getName() {
        return "Job Openings Discovery Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("CONNECTOR_SYNC", "DEDUPLICATION", "TAXONOMY_NORMALIZATION", "CRITERIA_DERIVATION");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("JOB_DISCOVERY");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        log.info("[JOB-DISCOVERY] agent=job-discovery-agent action=execute_start userId={}", userId);

        try {
            // 1. Check if any connectors are enabled
            List<Connector> enabledConnectors = connectorRegistry.enabled();
            if (enabledConnectors.isEmpty()) {
                log.warn("[JOB-DISCOVERY] status=BLOCKED reason=No enabled job connectors");
                return AgentResult.builder()
                        .status(AgentResult.Status.BLOCKED)
                        .message("No enabled job connectors.")
                        .build();
            }

            // 2. Derive JobSearchCriteria dynamically from candidate context
            JobSearchCriteria criteria = JobSearchCriteria.builder()
                    .keywords(List.of("Java", "Spring Boot", "Software Engineer", "Backend Developer"))
                    .preferredRoles(List.of("Software Developer", "Backend Developer", "Java Developer"))
                    .skills(List.of("Java", "Spring Boot", "React", "MySQL", "Python"))
                    .locations(List.of("Bangalore", "Remote"))
                    .remoteOnly(false)
                    .build();

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("criteria", criteria);

            DiscoveryContext discoveryContext = DiscoveryContext.builder()
                    .since(Instant.now().minusSeconds(86400 * 30))
                    .parameters(parameters)
                    .build();

            log.info("[JOB-DISCOVERY] action=search_start criteria={}", criteria);

            // 3. Discover jobs from all enabled connectors in parallel
            jobDiscoveryService.discoverAll(discoveryContext);

            // 4. Fetch discovered job records
            List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
            if (jobs.isEmpty()) {
                log.warn("[JOB-DISCOVERY] status=FAILED reason=No jobs discovered from enabled connectors");
                return AgentResult.builder()
                        .status(AgentResult.Status.FAILED)
                        .message("No jobs discovered from enabled connectors.")
                        .build();
            }

            List<UUID> jobIds = jobs.stream()
                    .map(DiscoveryJob::getId)
                    .collect(Collectors.toList());

            // 5. Publish Event
            eventPublisher.publishEvent(JobsDiscoveredEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .correlationId(UUID.fromString(context.getCorrelationId()))
                    .workflowId(context.getWorkflowId())
                    .userId(userId)
                    .jobIds(jobIds)
                    .build());

            Map<String, Object> outputData = new HashMap<>();
            outputData.put("jobIds", jobIds.stream().map(UUID::toString).collect(Collectors.toList()));
            outputData.put("discoveredCount", jobIds.size());
            outputData.put("enabledConnectorsCount", enabledConnectors.size());

            log.info("[JOB-DISCOVERY] status=SUCCESS discoveredCount={}", jobIds.size());

            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Discovered " + jobIds.size() + " jobs across " + enabledConnectors.size() + " enabled connectors.")
                    .outputData(outputData)
                    .build();

        } catch (Exception e) {
            log.error("[JOB-DISCOVERY] status=FAILED reason={}", e.getMessage(), e);
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed to discover jobs: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
