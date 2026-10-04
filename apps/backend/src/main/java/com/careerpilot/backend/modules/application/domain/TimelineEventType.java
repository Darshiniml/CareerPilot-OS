package com.careerpilot.backend.modules.application.domain;

/**
 * Deterministic timeline event vocabulary for communication-derived application intelligence
 * (Milestone 22.4). Mirrors {@link com.careerpilot.backend.modules.communication.domain.CommunicationClassification}
 * one-to-one except UNKNOWN, which never produces a timeline event because it carries no evidence.
 */
public enum TimelineEventType {
    APPLICATION_RECEIVED,
    APPLICATION_UNDER_REVIEW,
    ASSESSMENT_REQUEST,
    INTERVIEW_INVITATION,
    INTERVIEW_RESCHEDULED,
    ADDITIONAL_INFORMATION_REQUESTED,
    REJECTION,
    OFFER
}
