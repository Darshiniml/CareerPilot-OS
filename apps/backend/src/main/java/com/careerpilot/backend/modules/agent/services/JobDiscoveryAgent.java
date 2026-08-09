package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.domain.JobSearchCriteria;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.profile.repositories.*;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
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
    private final UserPreferredRoleRepository preferredRoleRepository;
    private final UserPreferredLocationRepository preferredLocationRepository;
    private final ExperienceRepository experienceRepository;
    private final ProjectRepository projectRepository;
    private final ResumeRepository resumeRepository;

    public JobDiscoveryAgent(JobDiscoveryService jobDiscoveryService,
                             ConnectorRegistry connectorRegistry,
                             ApplicationEventPublisher eventPublisher,
                             UserPreferredRoleRepository preferredRoleRepository,
                             UserPreferredLocationRepository preferredLocationRepository,
                             ExperienceRepository experienceRepository,
                             ProjectRepository projectRepository,
                             ResumeRepository resumeRepository) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.connectorRegistry = connectorRegistry;
        this.eventPublisher = eventPublisher;
        this.preferredRoleRepository = preferredRoleRepository;
        this.preferredLocationRepository = preferredLocationRepository;
        this.experienceRepository = experienceRepository;
        this.projectRepository = projectRepository;
        this.resumeRepository = resumeRepository;
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

            // 2. Derive JobSearchCriteria dynamically from candidate profile & resume
            JobSearchCriteria criteria = deriveCriteriaForUser(userId);

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

    public JobSearchCriteria deriveCriteriaForUser(UUID userId) {
        Set<String> keywords = new LinkedHashSet<>();
        Set<String> roles = new LinkedHashSet<>();
        Set<String> skills = new LinkedHashSet<>();
        Set<String> locations = new LinkedHashSet<>();

        // Load preferred roles
        try {
            preferredRoleRepository.findByUserId(userId).forEach(r -> {
                roles.add(r.getRoleName());
                keywords.add(r.getRoleName());
            });
        } catch (Exception ignored) {}

        // Load preferred locations
        try {
            preferredLocationRepository.findByUserId(userId).forEach(l -> locations.add(l.getLocationName()));
        } catch (Exception ignored) {}

        // Load experiences & projects
        try {
            experienceRepository.findByUserId(userId).forEach(exp -> {
                if (exp.getTitle() != null && !exp.getTitle().isBlank()) {
                    roles.add(exp.getTitle());
                    keywords.add(exp.getTitle());
                }
            });
        } catch (Exception ignored) {}

        try {
            projectRepository.findByUserId(userId).forEach(p -> {
                if (p.getName() != null && !p.getName().isBlank()) {
                    keywords.add(p.getName());
                }
            });
        } catch (Exception ignored) {}

        // Load default resume keywords
        try {
            Optional<Resume> defaultResume = resumeRepository.findDefaultByUserId(userId);
            if (defaultResume.isEmpty()) {
                List<Resume> active = resumeRepository.findActiveByUserId(userId);
                if (!active.isEmpty()) defaultResume = Optional.of(active.get(0));
            }
            defaultResume.ifPresent(r -> {
                if (r.getOriginalFilename() != null) {
                    keywords.add("Software Engineer");
                }
            });
        } catch (Exception ignored) {}

        // Defaults if candidate profile is not populated yet
        if (roles.isEmpty()) {
            roles.addAll(List.of("Software Developer", "Backend Developer", "Java Developer"));
        }
        if (keywords.isEmpty()) {
            keywords.addAll(List.of("Java", "Spring Boot", "Software Engineer", "Backend Developer", "React", "Python"));
        }
        if (skills.isEmpty()) {
            skills.addAll(List.of("Java", "Spring Boot", "React", "MySQL", "Python"));
        }
        if (locations.isEmpty()) {
            locations.addAll(List.of("Bangalore", "Remote"));
        }

        return JobSearchCriteria.builder()
                .keywords(new ArrayList<>(keywords))
                .preferredRoles(new ArrayList<>(roles))
                .skills(new ArrayList<>(skills))
                .locations(new ArrayList<>(locations))
                .remoteOnly(false)
                .build();
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
