package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.services.ApplicationOrchestratorService;
import com.careerpilot.backend.modules.application.services.EligibilityEngine;
import com.careerpilot.backend.modules.application.services.ResumeSelectionEngine;
import com.careerpilot.backend.modules.application.services.SubmissionPreflightService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.shared.events.ApplicationPreparedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class ApplicationAgent implements CareerAgent {

    private final JobDiscoveryService jobDiscoveryService;
    private final ConnectorRegistry connectorRegistry;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final ApplicationOrchestratorService applicationOrchestratorService;
    private final ApplicationEventPublisher eventPublisher;
    private final SubmissionPreflightService preflightService;
    private final com.careerpilot.backend.modules.ai.matching.MatchService matchService;

    public ApplicationAgent(JobDiscoveryService jobDiscoveryService,
                             ConnectorRegistry connectorRegistry,
                             ResumeRepository resumeRepository,
                             ResumeVersionRepository resumeVersionRepository,
                             ApplicationOrchestratorService applicationOrchestratorService,
                             ApplicationEventPublisher eventPublisher,
                             SubmissionPreflightService preflightService,
                            com.careerpilot.backend.modules.ai.matching.MatchService matchService) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.connectorRegistry = connectorRegistry;
        this.resumeRepository = resumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.applicationOrchestratorService = applicationOrchestratorService;
        this.eventPublisher = eventPublisher;
        this.preflightService = preflightService;
        this.matchService = matchService;
    }

    @Override
    public String getAgentId() {
        return "application-agent";
    }

    @Override
    public String getName() {
        return "Application Preparation & Submission Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("ELIGIBILITY_CHECK", "RESUME_SELECTION", "CONNECTOR_DISPATCH", "PREFLIGHT_CHECKS");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("APPLICATION_PREPARATION", "PREPARE_APPLICATION", "CHECK_PRE_FLIGHT", "WAIT_FOR_APPROVAL_OR_MANUAL_ACTION");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        AgentPolicy policy = context.getPolicy();
        String type = task.getTaskType();

        try {
            if ("CHECK_PRE_FLIGHT".equals(type)) {
                List<ApplicationRecord> apps = applicationOrchestratorService.listApplications().stream()
                        .filter(a -> a.getCandidateId().equals(userId))
                        .toList();

                List<Map<String, Object>> checks = new ArrayList<>();
                for (ApplicationRecord app : apps) {
                    SubmissionPreflightService.PreflightResult res = preflightService.evaluatePreflight(app, userId);
                    Map<String, Object> item = new HashMap<>();
                    item.put("applicationId", app.getApplicationId().toString());
                    item.put("passed", res.isAllowed());
                    List<String> details = new ArrayList<>();
                    if (res.getChecks() != null) {
                        for (SubmissionPreflightService.CheckResult cr : res.getChecks()) {
                            details.add(cr.getName() + ": " + cr.getDetails() + " (" + cr.isPassed() + ")");
                        }
                    }
                    item.put("reasons", details);
                    checks.add(item);
                }

                Map<String, Object> output = new HashMap<>();
                output.put("preflightChecks", checks);

                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Checked preflight criteria for " + apps.size() + " applications.")
                        .outputData(output)
                        .build();

            } else if ("WAIT_FOR_APPROVAL_OR_MANUAL_ACTION".equals(type)) {
                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Applications prepared and matching user approval/manual safety profiles.")
                        .build();

            } else {
                // APPLICATION_PREPARATION / PREPARE_APPLICATION
                Optional<Resume> defaultResume = resumeRepository.findDefaultByUserId(userId);
                if (defaultResume.isEmpty()) {
                    List<Resume> activeResumes = resumeRepository.findActiveByUserId(userId);
                    if (!activeResumes.isEmpty()) {
                        defaultResume = Optional.of(activeResumes.get(0));
                    }
                }

                if (defaultResume.isEmpty()) {
                    log.warn("[APPLICATION-AGENT] No active resume available for userId={}", userId);
                    return AgentResult.builder()
                            .status(AgentResult.Status.FAILED)
                            .message("No active resume available for application preparation.")
                            .build();
                }

                Resume resume = defaultResume.get();
                List<ResumeVersion> versions = resumeVersionRepository.findByResumeIdOrderByVersionNumberDesc(resume.getId());
                if (versions.isEmpty()) {
                    return AgentResult.builder()
                            .status(AgentResult.Status.FAILED)
                            .message("No active resume versions found.")
                            .build();
                }
                ResumeVersion version = versions.get(0);

                // Only jobs that genuinely match the candidate's processed resume, best first, within the
                // policy's daily limit. Nothing is created for unscored jobs.
                double minScore = policy != null && policy.getMinimumMatchScore() != null ? policy.getMinimumMatchScore() : 70.0;
                int maxNew = policy != null && policy.getMaxApplicationsPerDay() != null ? policy.getMaxApplicationsPerDay() : 5;
                List<DiscoveryJob> jobs = jobDiscoveryService.jobs().stream()
                        .map(j -> Map.entry(j, matchService.matchIfPossible(userId, j.getId())))
                        .filter(e -> e.getValue().isPresent() && e.getValue().get().getOverallScore() >= minScore)
                        .sorted((a, b) -> Double.compare(b.getValue().get().getOverallScore(), a.getValue().get().getOverallScore()))
                        .limit(maxNew)
                        .map(Map.Entry::getKey)
                        .toList();
                List<Map<String, Object>> preparedApps = new ArrayList<>();

                for (DiscoveryJob job : jobs) {
                    String connectorId = job.getConnectorId();
                    if (connectorId == null) {
                        continue; // no provenance: never invent a source
                    }
                    try {
                        connectorRegistry.get(connectorId);
                    } catch (Exception e) {
                        log.warn("[APPLICATION-AGENT] Connector {} not active for job {}; application will require manual action.", connectorId, job.getId());
                        connectorId = "manual-fallback";
                    }

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", "autonomous-workflow");
                    metadata.put("applyUrl", job.getSourceUrl() != null ? job.getSourceUrl() : "");

                    ApplicationRecord appRecord;
                    try {
                        appRecord = applicationOrchestratorService.createApplication(
                                userId,
                                null, // company records are not created from postings; never a random id
                                job.getId(),
                                connectorId,
                                metadata
                        );
                    } catch (IllegalArgumentException e) {
                        continue;
                    }

                    SubmissionPreflightService.PreflightResult preflight = preflightService.evaluatePreflight(appRecord, userId);

                    Map<String, Object> pApp = new HashMap<>();
                    pApp.put("applicationId", appRecord.getApplicationId().toString());
                    pApp.put("jobId", job.getId().toString());
                    pApp.put("title", job.getTitle());
                    pApp.put("company", job.getCompany());
                    pApp.put("workflowState", appRecord.getWorkflowState().name());
                    pApp.put("preflightAllowed", preflight.isAllowed());
                    preparedApps.add(pApp);
                }

                Map<String, Object> outputData = new HashMap<>();
                outputData.put("applications", preparedApps);
                outputData.put("count", preparedApps.size());

                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Prepared " + preparedApps.size() + " job applications (Automatic submission = " +
                                (policy != null && policy.getAllowAutomaticSubmission()) + ")")
                        .outputData(outputData)
                        .build();
            }

        } catch (Exception e) {
            log.error("[APPLICATION-AGENT] Failed during application execution: {}", e.getMessage(), e);
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed during execution: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
