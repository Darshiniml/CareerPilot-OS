package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.*;
import com.careerpilot.backend.modules.application.repositories.*;
import com.careerpilot.shared.events.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ApplicationOrchestratorService {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final ApplicationAuditRepository auditRepository;
    private final RetryAttemptRepository retryRepository;
    private final PlatformNotificationRepository notificationRepository;
    private final ApprovalPolicyRepository policyRepository;
    private final ApplicationWorkflowEngine workflowEngine;
    private final EligibilityEngine eligibilityEngine;
    private final ResumeSelectionEngine resumeSelectionEngine;
    private final ApprovalPolicyEngine approvalPolicyEngine;
    private final SubmissionAdapter submissionAdapter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApplicationRecord createApplication(UUID candidateId, UUID companyId, UUID jobId, String connectorId, Map<String, Object> metadata) {
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            throw new IllegalArgumentException("Duplicate application already exists");
        }

        Instant now = Instant.now();
        ApplicationRecord application = ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(candidateId)
                .companyId(companyId)
                .jobId(jobId)
                .connectorId(connectorId)
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

        if (eligibility.decision() == EligibilityDecision.REQUIRES_REVIEW) {
            application.setWorkflowState(WorkflowState.READY);
            recordHistory(application, WorkflowState.MATCHED, WorkflowState.READY, String.join("; ", eligibility.reasons()));
            audit(application, "ELIGIBILITY_REVIEW", WorkflowState.MATCHED, WorkflowState.READY, String.join("; ", eligibility.reasons()), null, null);
            applicationRepository.save(application);
            return application;
        }

        application.setWorkflowState(WorkflowState.ELIGIBLE);
        recordHistory(application, WorkflowState.MATCHED, WorkflowState.ELIGIBLE, "Eligibility checks passed");

        ResumeSelectionEngine.Selection selection = resumeSelectionEngine.select(resumeCandidates, Set.of(), "backend");
        application.setSelectedResumeId(selection.resumeId());
        application.setSelectedResumeVersion(selection.version());
        recordHistory(application, WorkflowState.ELIGIBLE, WorkflowState.READY, "Resume selected");
        application.setWorkflowState(WorkflowState.READY);

        ApprovalPolicyEngine.Context approvalContext = new ApprovalPolicyEngine.Context(
                eligibilityRequest.matchScore(),
                eligibilityRequest.remoteMatched(),
                policy != null ? policy.getTechnology() : null,
                policy != null ? policy.getLocation() : null
        );
        WorkflowState nextState = approvalPolicyEngine.nextState(policy, approvalContext);
        application.setWorkflowState(nextState == WorkflowState.APPROVED ? WorkflowState.APPROVED : WorkflowState.WAITING_APPROVAL);
        recordHistory(application, WorkflowState.READY, application.getWorkflowState(), "Approval decision prepared");
        audit(application, "PREPARE", WorkflowState.READY, application.getWorkflowState(), "Application prepared", null, null);
        applicationRepository.save(application);
        return application;
    }

    @Transactional
    public ApplicationRecord approve(UUID applicationId, UUID actorId, String ipAddress) {
        ApplicationRecord application = applicationRepository.findById(applicationId).orElseThrow(() -> new IllegalArgumentException("Application not found"));
        WorkflowState previousState = application.getWorkflowState();
        workflowEngine.validate(previousState, WorkflowState.APPROVED);
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
        workflowEngine.validate(previousState, WorkflowState.REJECTED);
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
        if (previousState == WorkflowState.APPROVED || previousState == WorkflowState.WAITING_APPROVAL) {
            application.setWorkflowState(WorkflowState.SUBMITTING);
        } else {
            workflowEngine.validate(previousState, WorkflowState.SUBMITTING);
            application.setWorkflowState(WorkflowState.SUBMITTING);
        }

        application.setUpdatedAt(Instant.now());
        recordHistory(application, previousState, WorkflowState.SUBMITTING, "Submission started");
        audit(application, "SUBMIT", previousState, WorkflowState.SUBMITTING, "Submission started", actorId, ipAddress);

        SubmissionAdapter.SubmissionResult result = submissionAdapter.submit(Map.of(
                "applicationId", application.getApplicationId().toString(),
                "candidateId", application.getCandidateId().toString(),
                "companyId", application.getCompanyId(),
                "jobId", application.getJobId().toString(),
                "resumeId", application.getSelectedResumeId(),
                "resumeVersion", application.getSelectedResumeVersion()
        ));

        if (result.isSuccess()) {
            application.setWorkflowState(WorkflowState.SUBMITTED);
            application.setSubmittedAt(Instant.now());
            application.setUpdatedAt(Instant.now());
            recordHistory(application, WorkflowState.SUBMITTING, WorkflowState.SUBMITTED, "Application submitted");
            audit(application, "SUBMIT_SUCCESS", WorkflowState.SUBMITTING, WorkflowState.SUBMITTED, result.getMessage(), actorId, ipAddress);
            notification(application, "APPLICATION_SUBMITTED", "Application submitted", "Your application was submitted successfully.");
            if (eventPublisher != null) {
                eventPublisher.publishEvent(ApplicationSubmittedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.SUBMITTED.name()).build());
            }
        } else {
            application.setWorkflowState(WorkflowState.FAILED);
            application.setFailureReason(result.getMessage());
            application.setUpdatedAt(Instant.now());
            recordHistory(application, WorkflowState.SUBMITTING, WorkflowState.FAILED, result.getMessage());
            audit(application, "SUBMIT_FAILED", WorkflowState.SUBMITTING, WorkflowState.FAILED, result.getMessage(), actorId, ipAddress);
            notification(application, "SUBMISSION_FAILED", "Submission failed", result.getMessage());
            if (eventPublisher != null) {
                eventPublisher.publishEvent(ApplicationFailedEvent.builder().eventId(UUID.randomUUID()).timestamp(Instant.now()).correlationId(applicationId).applicationId(applicationId).userId(actorId).jobId(application.getJobId()).status(WorkflowState.FAILED.name()).build());
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
    public Map<String, Object> statistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("applicationsSubmitted", applicationRepository.countByWorkflowState(WorkflowState.SUBMITTED));
        stats.put("approvalRate", 0.0);
        stats.put("submissionSuccessRate", 0.0);
        stats.put("failureRate", 0.0);
        stats.put("interviewRate", 0.0);
        stats.put("offerRate", 0.0);
        stats.put("averageMatchScore", 0.0);
        stats.put("averageTimeToSubmit", 0.0);
        stats.put("connectorPerformance", new HashMap<>());
        return stats;
    }

    @Transactional
    public ApprovalPolicy savePolicy(ApprovalPolicy policy) {
        policy.setUpdatedAt(Instant.now());
        return policyRepository.save(policy);
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
