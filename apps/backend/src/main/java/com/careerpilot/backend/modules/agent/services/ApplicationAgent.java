package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.services.ApplicationOrchestratorService;
import com.careerpilot.backend.modules.application.services.EligibilityEngine;
import com.careerpilot.backend.modules.application.services.ResumeSelectionEngine;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.shared.events.ApplicationPreparedEvent;
import com.careerpilot.shared.events.ApplicationSubmittedEvent;
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

    public ApplicationAgent(JobDiscoveryService jobDiscoveryService,
                             ConnectorRegistry connectorRegistry,
                             ResumeRepository resumeRepository,
                             ResumeVersionRepository resumeVersionRepository,
                             ApplicationOrchestratorService applicationOrchestratorService,
                             ApplicationEventPublisher eventPublisher) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.connectorRegistry = connectorRegistry;
        this.resumeRepository = resumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.applicationOrchestratorService = applicationOrchestratorService;
        this.eventPublisher = eventPublisher;
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
        return List.of("ELIGIBILITY_CHECK", "RESUME_SELECTION", "CONNECTOR_DISPATCH");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("APPLICATION_PREPARATION");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        AgentPolicy policy = context.getPolicy();
        
        try {
            // 1. Fetch default active resume
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
            
            // 2. Fetch matched jobs
            List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
            List<Map<String, Object>> preparedApps = new ArrayList<>();
            
            for (DiscoveryJob job : jobs) {
                String connectorId = job.getConnectorId() != null ? job.getConnectorId() : "local-jobs";
                
                // 3. Resolve connector safely
                try {
                    connectorRegistry.get(connectorId);
                } catch (Exception e) {
                    log.warn("[APPLICATION-AGENT] Connector {} not active for job {}, continuing with fallback.", connectorId, job.getId());
                    connectorId = "manual-fallback";
                }
                
                // 4. Create application record
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", "autonomous-workflow");
                metadata.put("applyUrl", job.getSourceUrl() != null ? job.getSourceUrl() : "");
                
                ApplicationRecord appRecord;
                try {
                    appRecord = applicationOrchestratorService.createApplication(
                            userId,
                            UUID.randomUUID(),
                            job.getId(),
                            connectorId,
                            metadata
                    );
                } catch (IllegalArgumentException e) {
                    // Skip duplicate creation gracefully
                    continue;
                }
                
                // 5. Evaluate and prepare
                EligibilityEngine.Request eligibilityRequest = new EligibilityEngine.Request(
                        85.0, 70.0, List.of("java"), List.of("java", "spring"),
                        2.0, 3.0, true, true, true, true, false, true
                );
                
                ResumeSelectionEngine.Candidate selectionCand = new ResumeSelectionEngine.Candidate(
                        resume, version, 85.0, Set.of("java"), "backend"
                );
                
                appRecord = applicationOrchestratorService.evaluateAndPrepare(
                        appRecord.getApplicationId(),
                        eligibilityRequest,
                        List.of(selectionCand),
                        null,
                        metadata
                );
                
                // Publish Prepared event
                eventPublisher.publishEvent(ApplicationPreparedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .correlationId(UUID.fromString(context.getCorrelationId()))
                        .workflowId(context.getWorkflowId())
                        .userId(userId)
                        .jobId(job.getId())
                        .build());
                
                // 6. Submit only if user policy allows automatic submission
                boolean submitted = false;
                if (policy != null && policy.getAllowAutomaticSubmission()) {
                    appRecord = applicationOrchestratorService.submit(appRecord.getApplicationId(), userId, "127.0.0.1");
                    submitted = appRecord.getWorkflowState().name().contains("SUBMITTED");
                }
                
                Map<String, Object> pApp = new HashMap<>();
                pApp.put("applicationId", appRecord.getApplicationId().toString());
                pApp.put("jobId", job.getId().toString());
                pApp.put("title", job.getTitle());
                pApp.put("company", job.getCompany());
                pApp.put("workflowState", appRecord.getWorkflowState().name());
                pApp.put("submittedAutomatically", submitted);
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
            
        } catch (Exception e) {
            log.error("[APPLICATION-AGENT] Failed during application preparation: {}", e.getMessage(), e);
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed during application preparation: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
