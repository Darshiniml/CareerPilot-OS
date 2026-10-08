package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationTimelineEvent;
import com.careerpilot.backend.modules.application.domain.TimelineEventOutcome;
import com.careerpilot.backend.modules.application.domain.TimelineEventType;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationTimelineEventRepository;
import com.careerpilot.backend.modules.application.services.ApplicationTrackingService;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
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
 * <p>Transition rules. There is exactly one lifecycle authority:
 * {@link ApplicationTrackingService#isTransitionAllowed} over {@code VALID_TRANSITIONS}. This engine
 * keeps no transition table of its own; it only adds communication-specific protections:</p>
 * <ul>
 *   <li>Terminal states ({@link ApplicationTrackingService#TERMINAL_STATES}) and OFFER are never moved by
 *       communication evidence; conflicting evidence is preserved with outcome
 *       {@code TERMINAL_STATE_PROTECTED} (an offer is only accepted/declined by the candidate).</li>
 *   <li>Out-of-order evidence: if a later-received communication has already changed the state, an
 *       older communication cannot move it ({@code OUT_OF_ORDER_IGNORED}); the evidence is kept.</li>
 *   <li>Transitions the lifecycle authority does not allow are recorded as
 *       {@code INVALID_TRANSITION_REJECTED}: no mutation, no fabricated success event.</li>
 *   <li>Allowed transitions are applied through {@link ApplicationTrackingService#transitionState} with
 *       actor type COMMUNICATION and the source communication id (full provenance in history).</li>
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
    /** States communication evidence may never move: lifecycle terminal states plus OFFER. */
    private static final Set<WorkflowState> COMMUNICATION_PROTECTED_STATES;

    static {
        TARGET_STATES.put(CommunicationClassification.APPLICATION_UNDER_REVIEW, WorkflowState.UNDER_REVIEW);
        TARGET_STATES.put(CommunicationClassification.ASSESSMENT_REQUEST, WorkflowState.ASSESSMENT);
        TARGET_STATES.put(CommunicationClassification.INTERVIEW_INVITATION, WorkflowState.INTERVIEW);
        TARGET_STATES.put(CommunicationClassification.REJECTION, WorkflowState.REJECTED);
        TARGET_STATES.put(CommunicationClassification.OFFER, WorkflowState.OFFER);

        Set<WorkflowState> protectedStates = EnumSet.copyOf(ApplicationTrackingService.TERMINAL_STATES);
        protectedStates.add(WorkflowState.OFFER);
        COMMUNICATION_PROTECTED_STATES = Collections.unmodifiableSet(protectedStates);
    }

    private final HrCommunicationRepository communicationRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationTimelineEventRepository timelineEventRepository;
    private final ApplicationTrackingService trackingService;

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
        if (COMMUNICATION_PROTECTED_STATES.contains(current)) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.TERMINAL_STATE_PROTECTED, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }
        if (isOlderThanAppliedEvidence(applicationId, communication)) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.OUT_OF_ORDER_IGNORED, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }
        if (!ApplicationTrackingService.isTransitionAllowed(current, target)) {
            ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                    TimelineEventOutcome.INVALID_TRANSITION_REJECTED, current, null, false, now);
            return toOutcome(event, application.getWorkflowState());
        }

        trackingService.transitionState(application.getApplicationId(), target, null,
                ApplicationHistory.ACTOR_COMMUNICATION,
                "HR communication evidence: " + classification
                        + " (communication " + communicationId + ", confidence " + confidence + ")",
                communicationId);

        ApplicationTimelineEvent event = persistEvent(application, communication, eventType,
                TimelineEventOutcome.STATE_TRANSITIONED, current, target, true, now);
        return toOutcome(event, target);
    }

    /**
     * True when a communication received later than this one has already changed the application's
     * state, i.e. this evidence was processed out of order and must not override newer evidence.
     */
    private boolean isOlderThanAppliedEvidence(UUID applicationId, HrCommunication communication) {
        Instant receivedAt = communication.getReceivedAt();
        if (receivedAt == null) {
            return false;
        }
        return timelineEventRepository
                .findTopByApplicationIdAndStateChangedTrueOrderByEventTimestampDesc(applicationId)
                .map(latest -> latest.getEventTimestamp() != null && latest.getEventTimestamp().isAfter(receivedAt))
                .orElse(false);
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
