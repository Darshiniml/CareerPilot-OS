package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.*;
import com.careerpilot.backend.modules.application.repositories.*;
import com.careerpilot.backend.modules.agent.domain.AgentPolicy;
import com.careerpilot.backend.modules.agent.repositories.AgentPolicyRepository;
import com.careerpilot.shared.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationOrchestratorService {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final ApplicationAuditRepository auditRepository;
    private final RetryAttemptRepository retryRepository;
    private final PlatformNotificationRepository notificationRepository;
    private final ApprovalPolicyRepository policyRepository;
    private final AgentPolicyRepository agentPolicyRepository;
    private final ApplicationWorkflowEngine workflowEngine;
    private final EligibilityEngine eligibilityEngine;
    private final ResumeSelectionEngine resumeSelectionEngine;
    private final ApprovalPolicyEngine approvalPolicyEngine;
    private final SubmissionAdapter submissionAdapter;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApplicationRecord createApplication(UUID candidateId, UUID companyId, UUID jobId, String connectorId, Map<String, Object> metadata) {
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            log.info("[APPLICATION] Candidate {} has already applied for jobId={}. Marking ALREADY_APPLIED.", candidateId, jobId);
            Optional<ApplicationRecord> existing = applicationRepository.findByCandidateIdAndJobId(candidateId, jobId);
            if (existing.isPresent()) {
                ApplicationRecord record = existing.get();
                record.setWorkflowState(WorkflowState.ALREADY_APPLIED);
                return applicationRepository.save(record);
            }
            throw new IllegalArgumentException("Duplicate application already exists");
        }

        Instant now = Instant.now();
        ApplicationRecord application = ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(candidateId)
                .companyId(companyId)
                .jobId(jobId)
                .connectorId(connectorId != null ? connectorId : "manual-fallback")
                .workflowState(WorkflowState.DISCOVERED)
                .retryCount(0)
                .metadata(metadata == null ? new HashMap<>() : new HashMap<>(metadata))
                .createdAt(now)
                .updatedAt(now)
                .build();

        application = applicationRepository.save(application);
        recordHistory(application, null, application.getWorkflowState(), "Application created");
        audit(application, "CREATE", null, application.getWorkflowState(), "Application created", null, null);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(ApplicationCreatedEvent.builder().eventId(UUID.randomUUID()).timestamp(now).correlationId(application.getApplicationId()).applicationId(application.getApplicationId()).userId(candidateId).jobId(jobId).status(application.getWorkflowState().name()).build());
        }
        return application;
    }

    @Transactional
    public ApplicationRecord evaluateAndPrepare(UUID applicationId, EligibilityEngine.Request eligibilityRequest, List<ResumeSelectionEngine.Candidate> resumeCandidates, ApprovalPolicy policy, Map<String, Object> metadata) {
        ApplicationRecord application = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        EligibilityEngine.Result eligibility = eligibilityEngine.evaluate(eligibilityRequest);
        application.setMetadata(mergeMetadata(application.getMetadata(), metadata));
        application.setWorkflowState(WorkflowState.MATCHED);
        recordHistory(application, WorkflowState.DISCOVERED, WorkflowState.MATCHED, "Matched with job intelligence");

        if (eligibility.decision() == EligibilityDecision.NOT_ELIGIBLE) {
            application.setWorkflowState(WorkflowState.REJECTED);
            recordHistory(application, WorkflowState.MATCHED, WorkflowState.REJECTED, String.join("; ", eligibility.reasons()));
            audit(application, "ELIGIBILITY_REJECTED", WorkflowState.MATCHED, WorkflowState.REJECTED, String.join("; ", eligibility.reasons()), null, null);
            applicationRepository.save(application);
            return application;
        }

        application.setWorkflowState(WorkflowState.ELIGIBLE);
        recordHistory(application, WorkflowState.MATCHED, WorkflowState.ELIGIBLE, "Eligibility checks passed");

        ResumeSelectionEngine.Selection selection = resumeSelectionEngine.select(resumeCandidates, Set.of(), "backend");
        application.setSelectedResumeId(selection.resumeId());
        application.setSelectedResumeVersion(selection.version());
        application.setWorkflowState(WorkflowState.APPLICATION_PREPARING);
        recordHistory(application, WorkflowState.ELIGIBLE, WorkflowState.APPLICATION_PREPARING, "Resume selected & application package building");

        // Check if submission connector is supported
        ApplicationSubmissionConnector submissionConnector = submissionRegistry.getConnector(application.getConnectorId());
        if (!submissionConnector.isSubmissionSupported()) {
            application.setWorkflowState(WorkflowState.MANUAL_ACTION_REQUIRED);
            application.setSubmissionMethod(ApplicationSubmissionMode.MANUAL_REQUIRED.name());
            recordHistory(application, WorkflowState.APPLICATION_PREPARING, WorkflowState.MANUAL_ACTION_REQUIRED, "Automated submission API unavailable. Candidate manual application required.");
            audit(application, "MANUAL_REQUIRED", WorkflowState.APPLICATION_PREPARING, WorkflowState.MANUAL_ACTION_REQUIRED, "Manual application required", null, null);
            applicationRepository.save(application);
            return application;
        }

        // Check user automation policy
        Optional<AgentPolicy> agentPolicyOpt = agentPolicyRepository.findByUserId(application.getCandidateId());
        boolean allowAutoSubmit = agentPolicyOpt.map(AgentPolicy::getAllowAutomaticSubmission).orElse(false);
        int maxPerDay = agentPolicyOpt.map(AgentPolicy::getMaxApplicationsPerDay).orElse(5);

        // Check daily application limit
        long todayCount = countTodaySubmissions(application.getCandidateId());
        if (todayCount >= maxPerDay) {
            application.setWorkflowState(WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT);
            recordHistory(application, WorkflowState.APPLICATION_PREPARING, WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT, "Daily application limit reached (" + todayCount + "/" + maxPerDay + ")");
            audit(application, "DAILY_LIMIT_BLOCKED", WorkflowState.APPLICATION_PREPARING, WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT, "Daily limit reached", null, null);
            applicationRepository.save(application);
            return application;
        }

        if (!allowAutoSubmit) {
            application.setWorkflowState(WorkflowState.READY_FOR_APPROVAL);
            recordHistory(application, WorkflowState.APPLICATION_PREPARING, WorkflowState.READY_FOR_APPROVAL, "Application prepared. Awaiting candidate approval.");
        } else {
            application.setWorkflowState(WorkflowState.APPROVED);
            recordHistory(application, WorkflowState.APPLICATION_PREPARING, WorkflowState.APPROVED, "Auto-approved per candidate safety policy.");
        }

        audit(application, "PREPARE", WorkflowState.APPLICATION_PREPARING, application.getWorkflowState(), "Application prepared", null, null);
        applicationRepository.save(application);
        return application;
    }

    @Transactional
    public ApplicationRecord approve(UUID applicationId, UUID actorId, String ipAddress) {
        ApplicationRecord application = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        WorkflowState previousState = application.getWorkflowState();
        application.setWorkflowState(WorkflowState.APPROVED);
        application.setUpdatedAt(Instant.now());
        recordHistory(application, previousState, WorkflowState.APPROVED, "Approved by user");
        audit(application, "APPROVE", previousState, WorkflowState.APPROVED, "Approved by user", actorId, ipAddress);
        applicationRepository.save(application);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(ApplicationApprovedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.APPROVED.name()).build());
        }
        return application;
    }

    @Transactional
    public ApplicationRecord reject(UUID applicationId, UUID actorId, String reason, String ipAddress) {
        ApplicationRecord application = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        WorkflowState previousState = application.getWorkflowState();
        application.setWorkflowState(WorkflowState.REJECTED);
        application.setUpdatedAt(Instant.now());
        recordHistory(application, previousState, WorkflowState.REJECTED, reason);
        audit(application, "REJECT", previousState, WorkflowState.REJECTED, reason, actorId, ipAddress);
        applicationRepository.save(application);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(ApplicationRejectedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.REJECTED.name()).build());
        }
        return application;
    }

    @Transactional
    public ApplicationRecord submit(UUID applicationId, UUID actorId, String ipAddress) {
        ApplicationRecord application = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        WorkflowState previousState = application.getWorkflowState();
        application.setWorkflowState(WorkflowState.SUBMISSION_IN_PROGRESS);
        application.setUpdatedAt(Instant.now());
        recordHistory(application, previousState, WorkflowState.SUBMISSION_IN_PROGRESS, "Submission execution started");
        audit(application, "SUBMIT", previousState, WorkflowState.SUBMISSION_IN_PROGRESS, "Submission started", actorId, ipAddress);

        ApplicationSubmissionConnector submissionConnector = submissionRegistry.getConnector(application.getConnectorId());
        if (!submissionConnector.isSubmissionSupported()) {
            application.setWorkflowState(WorkflowState.MANUAL_ACTION_REQUIRED);
            application.setSubmissionMethod(ApplicationSubmissionMode.MANUAL_REQUIRED.name());
            recordHistory(application, WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.MANUAL_ACTION_REQUIRED, "Submission API not supported for source. Direct candidate application required.");
            notification(application, "MANUAL_ACTION_REQUIRED", "Manual Application Required", "Please use the official apply link to complete your application.");
            applicationRepository.save(application);
            return application;
        }

        ApplicationSubmissionConnector.SubmissionResult result = submissionConnector.executeSubmission(application, application.getMetadata());

        if (result.isSuccess()) {
            application.setWorkflowState(WorkflowState.SUBMITTED_VERIFIED);
            application.setExternalApplicationId(result.getExternalApplicationId());
            application.setSubmissionMethod(result.getMode().name());
            application.setSubmittedAt(Instant.now());
            application.setLastVerifiedAt(Instant.now());
            application.setUpdatedAt(Instant.now());
            recordHistory(application, WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMITTED_VERIFIED, "Application submitted and verified.");
            audit(application, "SUBMIT_SUCCESS", WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMITTED_VERIFIED, result.getStatusMessage(), actorId, ipAddress);
            notification(application, "APPLICATION_SUBMITTED", "Application Submitted & Verified", "Your application was submitted successfully.");
            if (eventPublisher != null) {
                eventPublisher.publishEvent(ApplicationSubmittedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.SUBMITTED_VERIFIED.name()).build());
            }
        } else {
            application.setWorkflowState(WorkflowState.SUBMISSION_FAILED);
            application.setFailureReason(result.getFailureReason());
            application.setUpdatedAt(Instant.now());
            recordHistory(application, WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMISSION_FAILED, result.getFailureReason());
            audit(application, "SUBMIT_FAILED", WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMISSION_FAILED, result.getFailureReason(), actorId, ipAddress);
            notification(application, "SUBMISSION_FAILED", "Submission Failed", result.getFailureReason());
            if (eventPublisher != null) {
                eventPublisher.publishEvent(ApplicationFailedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.SUBMISSION_FAILED.name()).build());
            }
        }
        applicationRepository.save(application);
        return application;
    }

    @Transactional
    public ApplicationRecord retry(UUID applicationId, UUID actorId, String ipAddress) {
        ApplicationRecord application = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        WorkflowState previousState = application.getWorkflowState();
        int nextAttempt = application.getRetryCount() + 1;
        application.setRetryCount(nextAttempt);
        application.setWorkflowState(WorkflowState.RETRYING);
        application.setUpdatedAt(Instant.now());
        recordHistory(application, previousState, WorkflowState.RETRYING, "Retry scheduled");
        RetryAttempt retryAttempt = RetryAttempt.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .attemptNumber(nextAttempt)
                .reason(application.getFailureReason())
                .permanentFailure(nextAttempt >= 3)
                .nextAttemptAt(Instant.now().plusSeconds(30L * nextAttempt))
                .createdAt(Instant.now())
                .build();
        retryRepository.save(retryAttempt);
        audit(application, "RETRY", previousState, WorkflowState.RETRYING, "Retry scheduled", actorId, ipAddress);
        notification(application, "SUBMISSION_RETRYING", "Retry scheduled", "The application will be retried shortly.");
        if (eventPublisher != null) {
            eventPublisher.publishEvent(ApplicationRetriedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.RETRYING.name()).build());
        }
        applicationRepository.save(application);
        return application;
    }

    @Transactional(readOnly = true)
    public List<ApplicationRecord> listApplications() {
        return applicationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ApplicationRecord getApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
    }

    @Transactional(readOnly = true)
    public List<ApplicationHistory> history(UUID applicationId) {
        return historyRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statistics(UUID candidateId) {
        List<ApplicationRecord> applications = applicationRepository.findAll().stream()
                .filter(application -> candidateId.equals(application.getCandidateId()))
                .toList();
        long total = applications.size();
        long submitted = applications.stream().filter(application -> application.getWorkflowState() == WorkflowState.SUBMITTED || application.getWorkflowState() == WorkflowState.SUBMITTED_VERIFIED).count();
        long approved = applications.stream().filter(application -> application.getWorkflowState() == WorkflowState.APPROVED).count();
        long failed = applications.stream().filter(application -> application.getWorkflowState() == WorkflowState.APPLICATION_FAILED || application.getWorkflowState() == WorkflowState.SUBMISSION_FAILED || application.getWorkflowState() == WorkflowState.FAILED).count();
        long interviews = applications.stream().filter(application -> application.getWorkflowState() == WorkflowState.INTERVIEW).count();
        long offers = applications.stream().filter(application -> application.getWorkflowState() == WorkflowState.OFFER).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("applicationsSubmitted", submitted);
        stats.put("approvalRate", total == 0 ? null : ((double) approved / total) * 100);
        stats.put("submissionSuccessRate", total == 0 ? null : ((double) submitted / total) * 100);
        stats.put("failureRate", total == 0 ? null : ((double) failed / total) * 100);
        stats.put("interviewRate", total == 0 ? null : ((double) interviews / total) * 100);
        stats.put("offerRate", total == 0 ? null : ((double) offers / total) * 100);
        stats.put("averageMatchScore", null);
        stats.put("averageTimeToSubmit", null);
        stats.put("connectorPerformance", new HashMap<>());
        return stats;
    }

    @Transactional
    public ApprovalPolicy savePolicy(ApprovalPolicy policy) {
        policy.setUpdatedAt(Instant.now());
        return policyRepository.save(policy);
    }

    private long countTodaySubmissions(UUID candidateId) {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return applicationRepository.findAll().stream()
                .filter(a -> a.getCandidateId().equals(candidateId))
                .filter(a -> a.getSubmittedAt() != null && a.getSubmittedAt().isAfter(startOfDay))
                .count();
    }

    private void recordHistory(ApplicationRecord application, WorkflowState fromState, WorkflowState toState, String reason) {
        historyRepository.save(ApplicationHistory.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getApplicationId())
                .fromState(fromState)
                .toState(toState)
                .reason(reason)
                .createdAt(Instant.now())
                .build());
    }

    private void audit(ApplicationRecord application, String action, WorkflowState fromState, WorkflowState toState, String reason, UUID userId, String ipAddress) {
        auditRepository.save(ApplicationAudit.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getApplicationId())
                .userId(userId)
                .action(action)
                .fromState(fromState == null ? null : fromState.name())
                .toState(toState == null ? null : toState.name())
                .reason(reason)
                .ipAddress(ipAddress)
                .correlationId(application.getApplicationId().toString())
                .createdAt(Instant.now())
                .build());
    }

    private void notification(ApplicationRecord application, String type, String title, String message) {
        notificationRepository.save(PlatformNotification.builder()
                .id(UUID.randomUUID())
                .candidateId(application.getCandidateId())
                .applicationId(application.getApplicationId())
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .createdAt(Instant.now())
                .build());
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> merged = new HashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return merged;
    }
}
