package com.careerpilot.backend.modules.communication.api;

import com.careerpilot.backend.modules.communication.services.ApplicationStateTransitionService.TransitionOutcome;

import java.util.UUID;

/**
 * Client-facing result of processing a classified communication into application intelligence.
 * Every field is server-derived; nothing here can be supplied or overridden by the caller.
 */
public record CommunicationProcessingResponse(
        UUID communicationId,
        UUID applicationId,
        String eventType,
        String outcome,
        boolean stateChanged,
        String previousState,
        String newState,
        String applicationState,
        UUID timelineEventId) {

    public static CommunicationProcessingResponse from(UUID communicationId, TransitionOutcome outcome) {
        return new CommunicationProcessingResponse(
                communicationId,
                outcome.applicationId(),
                outcome.eventType() == null ? null : outcome.eventType().name(),
                outcome.outcome().name(),
                outcome.stateChanged(),
                outcome.previousState() == null ? null : outcome.previousState().name(),
                outcome.newState() == null ? null : outcome.newState().name(),
                outcome.applicationState() == null ? null : outcome.applicationState().name(),
                outcome.timelineEventId());
    }
}
