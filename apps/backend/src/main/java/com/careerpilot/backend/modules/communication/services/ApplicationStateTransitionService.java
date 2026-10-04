package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationTimelineEvent;
import com.careerpilot.backend.modules.application.domain.TimelineEventOutcome;
import com.careerpilot.backend.modules.application.domain.TimelineEventType;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationTimelineEventRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic application-state transition engine for classified HR communications (Milestone 22.4).
 *
 * <p>Strictly separates AI interpretation from state authority: the M22.3 classification is only
 * evidence; this engine alone decides — with fixed, documented rules — whether that evidence may
 * produce a timeline event and/or a canonical {@link WorkflowState} transition. The AI never names
 * an application state, and nothing here trusts client-supplied identity, state, or event data.</p>
 *
 * <p>Classification → event mapping (every classification is evaluated):</p>
 * <ul>
 *   <li>APPLICATION_RECEIVED → timeline event only (no canonical "received" state exists; the
 *       application record itself is the receipt)</li>
 *   <li>APPLICATION_UNDER_REVIEW → {@code UNDER_REVIEW}</li>
 *   <li>ASSESSMENT_REQUEST → {@code ASSESSMENT}</li>
 *   <li>INTERVIEW_INVITATION → {@code INTERVIEW}</li>
 *   <li>INTERVIEW_RESCHEDULED → timeline event only (no canonical rescheduled state exists)</li>
 *   <li>ADDITIONAL_INFORMATION_REQUESTED → timeline event only (an information request is not a
 *       lifecycle stage)</li>
 *   <li>REJECTION → {@code REJECTED}</li>
 *   <li>OFFER → {@code OFFER}</li>
 *   <li>UNKNOWN → no event, no transition (no evidence to act on)</li>
 * </ul>
 *
 * <p>Transition rules over the canonical lifecycle ranks UNDER_REVIEW(1) &lt; ASSESSMENT(2) &lt;
 * INTERVIEW(3) &lt; OFFER(4):</p>
 * <ul>
 *   <li>Terminal states (OFFER, REJECTED, REJECTED_BY_COMPANY, WITHDRAWN, COMPLETED, ARCHIVED) are
 *       never moved by communication evidence; conflicting evidence is preserved as a timeline
 *       event with outcome {@code TERMINAL_STATE_PROTECTED}.</li>
 *   <li>REJECTED is a valid target from any non-terminal state (a company may reject at any stage).</li>
 *   <li>A ranked target is valid only if it moves strictly forward; states outside the ranked
 *       lifecycle (e.g. DISCOVERED … SUBMITTED_VERIFIED) accept any ranked target because HR
 *       evidence proves the external process is ahead of the local record.</li>
 *   <li>Anything else is rejected as {@code INVALID_TRANSITION_REJECTED}: no mutation, no fabricated
 *       success event, and the M22.3 classification stays intact.</li>
 * </ul>
 *
 * <p>Low-confidence policy: a classification below {@link #MIN_CONFIDENCE_FOR_STATE_ACTION} (0.5)
 * is withheld — stored classification untouched, no timeline event, no state change. The threshold
 * is derived from the deterministic ai-service classifier, whose committed labels always score
 * ≥ 0.61 (0.4 + commit-threshold 0.35 × 0.6) while ambiguous/insufficient results are capped at
 * 0.4; 0.5 cleanly separates decisive from uncertain evidence.</p>
 *
 * <p>Idempotency: the unique identity {@code (applicationId, communicationId, eventType)} guarantees
 * that repeated processing of the same communication never creates duplicate events; replays return
 * the persisted event without mutating anything.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationStateTransitionService {

    static final double MIN_CONFIDENCE_FOR_STATE_ACTION = 0.5;

    private static final Map<CommunicationClassification, WorkflowState> TARGET_STATES =
            new EnumMap<>(CommunicationClassification.class);
    private static final Map<WorkflowState, Integer> LIFECYCLE_RANK = new EnumMap<>(WorkflowState.class);
    private static final Set<WorkflowState> TERMINAL_STATES = Set.of(
            WorkflowState.OFFER,
            WorkflowState.REJECTED,
            WorkflowState.REJECTED_BY_COMPANY,
            WorkflowState.WITHDRAWN,
            WorkflowState.COMPLETED,
            WorkflowState.ARCHIVED);

    static {
        TARGET_STATES.put(CommunicationClassification.APPLICATION_UNDER_REVIEW, WorkflowState.UNDER_REVIEW);
        TARGET_STATES.put(CommunicationClassification.ASSESSMENT_REQUEST, WorkflowState.ASSESSMENT);
        TARGET_STATES.put(CommunicationClassification.INTERVIEW_INVITATION, WorkflowState.INTERVIEW);
        TARGET_STATES.put(CommunicationClassification.REJECTION, WorkflowState.REJECTED);
        TARGET_STATES.put(CommunicationClassification.OFFER, WorkflowState.OFFER);

        LIFECYCLE_RANK.put(WorkflowState.UNDER_REVIEW, 1);
        LIFECYCLE_RANK.put(WorkflowState.ASSESSMENT, 2);
        LIFECYCLE_RANK.put(WorkflowState.INTERVIEW, 3);
        LIFECYCLE_RANK.put(WorkflowState.OFFER, 4);
    }

    private final HrCommunicationRepository communicationRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final ApplicationTimelineEventRepository timelineEventRepository;

    /**
     * Deterministic processing result. All values are server-derived; {@code applicationState} is
     * the application's state after processing (null when no application is involved).
     */
    public record TransitionOutcome(
            TimelineEventOutcome outcome,
            UUID applicationId,
            UUID timelineEventId,
            TimelineEventType eventType,
            boolean stateChanged,
            WorkflowState previousState,
            WorkflowState newState,
            WorkflowState applicationState) {

        static TransitionOutcome withoutEvent(TimelineEventOutcome outcome, WorkflowState applicationState) {
            return new TransitionOutcome(outcome, null, null, null, false, null, null, applicationState);
        }
    }

    @Transactional
    public TransitionOutcome processClassification(UUID candidateId, UUID communicationId) {
        HrCommunication communication = communicationRepository
                .findByIdAndCandidateId(communicationId, candidateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found for candidate " + candidateId));

        if (communication.getProcessingStatus() != CommunicationProcessingStatus.PROCESSED
                || communication.getClassification() == null) {
            return TransitionOutcome.withoutEvent(TimelineEventOutcome.NOT_CLASSIFIED, null);
        }

        CommunicationClassification classification = communication.getClassification();
        if (classification == CommunicationClassification.UNKNOWN) {
            return TransitionOutcome.withoutEvent(TimelineEventOutcome.NO_ACTIONABLE_CLASSIFICATION, null);
        }

        UUID applicationId = communication.getMatchedApplicationId();
        if (applicationId == null) {
            return TransitionOutcome.withoutEvent(TimelineEventOutcome.UNMATCHED_NO_APPLICATION, null);
        }
        ApplicationRecord application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null || !application.getCandidateId().equals(candidateId)) {
            // Never act on, guess, or fabricate an application the candidate does not own.
            log.warn("Communication {} references application {} that is absent or not owned by candidate {}",
                    communicationId, applicationId, candidateId);
            return TransitionOutcome.withoutEvent(TimelineEventOutcome.UNMATCHED_NO_APPLICATION, null);
        }

        TimelineEventType eventType = TimelineEventType.valueOf(classification.name());

        Optional<ApplicationTimelineEvent> existing = timelineEventRepository
                .findByApplicationIdAndCommunicationIdAndEventType(applicationId, communicationId, eventType);
        if (existing.isPresent()) {
            return replay(existing.get(), application.getWorkflowState());
        }

        Double confidence = communication.getClassificationConfidence();
        if (confidence == null || confidence < MIN_CONFIDENCE_FOR_STATE_ACTION) {
            return new TransitionOutcome(TimelineEventOutcome.WITHHELD_LOW_CONFIDENCE, applicationId,
                    null, eventType, false, null, null, application.getWorkflowState());
        }

        WorkflowState current = application.getWorkflowState();
        WorkflowState target = TARGET_STATES.get(classification);
        Instant now = Instant.now();

        if (target == null) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.EVENT_RECORDED, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }
        if (current == target) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.ALREADY_IN_TARGET_STATE, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }
        if (TERMINAL_STATES.contains(current)) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.TERMINAL_STATE_PROTECTED, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }
        if (!isValidTransition(current, target)) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.INVALID_TRANSITION_REJECTED, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }

        application.setWorkflowState(target);
        application.setUpdatedAt(now);
        applicationRepository.save(application);

        historyRepository.save(ApplicationHistory.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getApplicationId())
                .fromState(current)
                .toState(target)
                .actorId(candidateId)
                .reason("HR communication evidence: " + classification
                        + " (communication " + communicationId + ", confidence " + confidence + ")")
                .createdAt(now)
                .build());

        ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                TimelineEventOutcome.STATE_TRANSITIONED, current, target, true, now);
        return toOutcome(event, target);
    }

    private boolean isValidTransition(WorkflowState current, WorkflowState target) {
        if (target == WorkflowState.REJECTED) {
            return true;
        }
        Integer currentRank = LIFECYCLE_RANK.get(current);
        if (currentRank == null) {
            return true;
        }
        Integer targetRank = LIFECYCLE_RANK.get(target);
        return targetRank != null && targetRank > currentRank;
    }

    private ApplicationTimelineEvent persistEvent(ApplicationRecord application,
                                                  HrCommunication communication,
                                                  TimelineEventType eventType,
                                                  TimelineEventOutcome outcome,
                                                  WorkflowState previousState,
                                                  WorkflowState newState,
                                                  boolean stateChanged,
                                                  Instant now) {
        ApplicationTimelineEvent event = ApplicationTimelineEvent.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getApplicationId())
                .communicationId(communication.getId())
                .eventType(eventType)
                .outcome(outcome)
                .previousState(previousState)
                .newState(newState)
                .stateChanged(stateChanged)
                .classification(communication.getClassification())
                .classificationConfidence(communication.getClassificationConfidence())
                .evidence(truncate(communication.getClassificationReason()))
                .eventTimestamp(communication.getReceivedAt())
                .source(ApplicationTimelineEvent.SOURCE_COMMUNICATION_CLASSIFICATION)
                .createdAt(now)
                .build();
        return timelineEventRepository.save(event);
    }

    private TransitionOutcome replay(ApplicationTimelineEvent event, WorkflowState currentState) {
        return new TransitionOutcome(event.getOutcome(), event.getApplicationId(), event.getId(),
                event.getEventType(), event.isStateChanged(), event.getPreviousState(),
                event.getNewState(), currentState);
    }

    private TransitionOutcome toOutcome(ApplicationTimelineEvent event, WorkflowState currentState) {
        return new TransitionOutcome(event.getOutcome(), event.getApplicationId(), event.getId(),
                event.getEventType(), event.isStateChanged(), event.getPreviousState(),
                event.getNewState(), currentState);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
