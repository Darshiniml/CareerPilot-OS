package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.*;
import com.careerpilot.backend.modules.application.repositories.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class ApplicationTrackingService {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final ApplicationVerificationRepository verificationRepository;
    private final PlatformNotificationRepository notificationRepository;

    private static final Map<WorkflowState, Set<WorkflowState>> VALID_TRANSITIONS = new EnumMap<>(WorkflowState.class);

    static {
        VALID_TRANSITIONS.put(WorkflowState.DISCOVERED, Set.of(WorkflowState.MATCHED, WorkflowState.ELIGIBLE, WorkflowState.APPLICATION_PREPARING, WorkflowState.ALREADY_APPLIED));
        VALID_TRANSITIONS.put(WorkflowState.MATCHED, Set.of(WorkflowState.ELIGIBLE, WorkflowState.APPLICATION_PREPARING, WorkflowState.REJECTED));
        VALID_TRANSITIONS.put(WorkflowState.ELIGIBLE, Set.of(WorkflowState.APPLICATION_PREPARING, WorkflowState.READY_FOR_APPROVAL, WorkflowState.MANUAL_ACTION_REQUIRED));
        VALID_TRANSITIONS.put(WorkflowState.APPLICATION_PREPARING, Set.of(WorkflowState.APPLICATION_READY, WorkflowState.READY_FOR_APPROVAL, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT));
        VALID_TRANSITIONS.put(WorkflowState.APPLICATION_READY, Set.of(WorkflowState.READY_FOR_APPROVAL, WorkflowState.APPROVED, WorkflowState.REJECTED));
        VALID_TRANSITIONS.put(WorkflowState.READY_FOR_APPROVAL, Set.of(WorkflowState.APPROVED, WorkflowState.REJECTED));
        VALID_TRANSITIONS.put(WorkflowState.APPROVED, Set.of(WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.APPLICATION_FAILED));
        VALID_TRANSITIONS.put(WorkflowState.SUBMISSION_IN_PROGRESS, Set.of(WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.VERIFICATION_PENDING, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.SUBMISSION_FAILED));
        VALID_TRANSITIONS.put(WorkflowState.SUBMITTED, Set.of(WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.UNDER_REVIEW, WorkflowState.INTERVIEW, WorkflowState.REJECTED, WorkflowState.OFFER));
        VALID_TRANSITIONS.put(WorkflowState.VERIFICATION_PENDING, Set.of(WorkflowState.SUBMITTED_VERIFIED, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.SUBMISSION_FAILED));
        VALID_TRANSITIONS.put(WorkflowState.SUBMITTED_VERIFIED, Set.of(WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW, WorkflowState.REJECTED, WorkflowState.OFFER, WorkflowState.WITHDRAWN));
        VALID_TRANSITIONS.put(WorkflowState.MANUAL_ACTION_REQUIRED, Set.of(WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW, WorkflowState.REJECTED, WorkflowState.OFFER, WorkflowState.WITHDRAWN));
        VALID_TRANSITIONS.put(WorkflowState.UNDER_REVIEW, Set.of(WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW, WorkflowState.REJECTED, WorkflowState.OFFER, WorkflowState.WITHDRAWN));
        VALID_TRANSITIONS.put(WorkflowState.ASSESSMENT, Set.of(WorkflowState.INTERVIEW, WorkflowState.REJECTED, WorkflowState.OFFER, WorkflowState.WITHDRAWN));
        VALID_TRANSITIONS.put(WorkflowState.INTERVIEW, Set.of(WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.WITHDRAWN));
        VALID_TRANSITIONS.put(WorkflowState.SUBMISSION_FAILED, Set.of(WorkflowState.RETRYING, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.FAILED));
        VALID_TRANSITIONS.put(WorkflowState.RETRYING, Set.of(WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMITTED, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.FAILED));
    }

    public ApplicationTrackingService(
            ApplicationRecordRepository applicationRepository,
            ApplicationHistoryRepository historyRepository,
            ApplicationVerificationRepository verificationRepository,
            PlatformNotificationRepository notificationRepository) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.verificationRepository = verificationRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public ApplicationRecord transitionState(UUID applicationId, WorkflowState targetState, UUID actorId, String actorType, String reason) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application record not found"));

        WorkflowState currentState = application.getWorkflowState();
        if (currentState == targetState) {
            return application;
        }

        Set<WorkflowState> allowed = VALID_TRANSITIONS.getOrDefault(currentState, Collections.emptySet());
        if (!allowed.contains(targetState) && targetState != WorkflowState.WITHDRAWN && targetState != WorkflowState.REJECTED) {
            throw new IllegalStateException("Invalid state transition from " + currentState + " to " + targetState);
        }

        application.setWorkflowState(targetState);
        application.setUpdatedAt(Instant.now());
        applicationRepository.save(application);

        ApplicationHistory history = ApplicationHistory.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .fromState(currentState)
                .toState(targetState)
                .actorId(actorId)
                .reason(reason != null ? reason : "Transition to " + targetState)
                .createdAt(Instant.now())
                .build();
        historyRepository.save(history);

        // Notify user for important state changes
        if (targetState == WorkflowState.INTERVIEW || targetState == WorkflowState.OFFER || targetState == WorkflowState.REJECTED) {
            PlatformNotification notif = PlatformNotification.builder()
                    .id(UUID.randomUUID())
                    .candidateId(application.getCandidateId())
                    .applicationId(applicationId)
                    .type(targetState.name() + "_REPORTED")
                    .title("Application Status Update: " + targetState)
                    .message("Application status updated to " + targetState + " (" + (actorType != null ? actorType : "USER") + ").")
                    .read(false)
                    .createdAt(Instant.now())
                    .build();
            notificationRepository.save(notif);
        }

        return application;
    }

    @Transactional
    public ApplicationVerificationResult verifyApplicationWithEvidence(UUID applicationId, String evidenceType, String evidenceReference, String confirmationId, UUID requestingUserId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application record not found"));

        if (!application.getCandidateId().equals(requestingUserId)) {
            throw new SecurityException("User does not own this application");
        }

        if (evidenceType == null || evidenceType.isBlank() || (evidenceReference == null && confirmationId == null)) {
            throw new IllegalArgumentException("Concrete verification evidence is required");
        }

        ApplicationVerificationResult verification = ApplicationVerificationResult.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .verificationStatus("VERIFIED")
                .evidenceType(evidenceType)
                .evidenceReference(evidenceReference)
                .confirmationId(confirmationId)
                .reason("Empirical evidence verified")
                .verifiedAt(Instant.now())
                .build();
        verificationRepository.save(verification);

        // Advance workflow state to SUBMITTED_VERIFIED
        transitionState(applicationId, WorkflowState.SUBMITTED_VERIFIED, requestingUserId, "USER", "Verified with evidence ID: " + confirmationId);

        return verification;
    }

    public List<ApplicationTimelineEvent> getTimeline(UUID applicationId, UUID requestingUserId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application record not found"));

        if (!application.getCandidateId().equals(requestingUserId)) {
            throw new SecurityException("User does not own this application");
        }

        List<ApplicationHistory> histories = historyRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        List<ApplicationTimelineEvent> timeline = new ArrayList<>();

        for (int i = 0; i < histories.size(); i++) {
            ApplicationHistory h = histories.get(i);
            Instant start = h.getCreatedAt();
            Instant end = (i > 0) ? histories.get(i - 1).getCreatedAt() : Instant.now();
            long durationSeconds = Math.max(0, Duration.between(start, end).getSeconds());

            timeline.add(ApplicationTimelineEvent.builder()
                    .fromState(h.getFromState() != null ? h.getFromState().name() : "INITIAL")
                    .toState(h.getToState().name())
                    .timestamp(h.getCreatedAt())
                    .actorId(h.getActorId())
                    .actorType(h.getActorId() != null ? "USER" : "SYSTEM")
                    .reason(h.getReason())
                    .durationSeconds(durationSeconds)
                    .build());
        }

        return timeline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationTimelineEvent {
        private String fromState;
        private String toState;
        private Instant timestamp;
        private UUID actorId;
        private String actorType;
        private String reason;
        private long durationSeconds;
    }
}
