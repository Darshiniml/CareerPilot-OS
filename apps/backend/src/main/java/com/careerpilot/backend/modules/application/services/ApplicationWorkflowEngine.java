package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.WorkflowState;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationWorkflowEngine {
    private final Map<WorkflowState, Set<WorkflowState>> transitions = new EnumMap<>(WorkflowState.class);

    public ApplicationWorkflowEngine() {
        allow(WorkflowState.DISCOVERED, WorkflowState.MATCHED, WorkflowState.APPLICATION_PREPARING, WorkflowState.ALREADY_APPLIED);
        allow(WorkflowState.MATCHED, WorkflowState.ELIGIBLE, WorkflowState.REJECTED, WorkflowState.APPLICATION_PREPARING);
        allow(WorkflowState.ELIGIBLE, WorkflowState.APPLICATION_PREPARING, WorkflowState.APPLICATION_READY, WorkflowState.REJECTED);
        allow(WorkflowState.APPLICATION_PREPARING, WorkflowState.APPLICATION_READY, WorkflowState.READY_FOR_APPROVAL, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.UNSUPPORTED_CONNECTOR, WorkflowState.ALREADY_APPLIED, WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT, WorkflowState.APPLICATION_FAILED);
        allow(WorkflowState.APPLICATION_READY, WorkflowState.READY_FOR_APPROVAL, WorkflowState.APPROVED, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT);
        allow(WorkflowState.READY_FOR_APPROVAL, WorkflowState.APPROVED, WorkflowState.REJECTED, WorkflowState.MANUAL_ACTION_REQUIRED);
        allow(WorkflowState.APPROVED, WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMITTING, WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT);
        allow(WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.SUBMISSION_FAILED, WorkflowState.APPLICATION_FAILED);
        allow(WorkflowState.SUBMITTING, WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.SUBMISSION_FAILED, WorkflowState.APPLICATION_FAILED, WorkflowState.FAILED);
        allow(WorkflowState.SUBMITTED, WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.TRACKING, WorkflowState.COMPLETED);
        allow(WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.SUBMISSION_FAILED);
        allow(WorkflowState.SUBMITTED_VERIFIED, WorkflowState.TRACKING, WorkflowState.COMPLETED);
        allow(WorkflowState.MANUAL_ACTION_REQUIRED, WorkflowState.APPROVED, WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.ARCHIVED);
        allow(WorkflowState.FAILED, WorkflowState.RETRYING, WorkflowState.ARCHIVED);
        allow(WorkflowState.APPLICATION_FAILED, WorkflowState.RETRYING, WorkflowState.ARCHIVED);
        allow(WorkflowState.SUBMISSION_FAILED, WorkflowState.RETRYING, WorkflowState.ARCHIVED);
        allow(WorkflowState.RETRYING, WorkflowState.SUBMISSION_IN_PROGRESS, WorkflowState.SUBMITTING, WorkflowState.FAILED, WorkflowState.APPLICATION_FAILED);
        allow(WorkflowState.TRACKING, WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.COMPLETED);
        allow(WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.COMPLETED);
        allow(WorkflowState.OFFER, WorkflowState.COMPLETED, WorkflowState.ARCHIVED);
        allow(WorkflowState.REJECTED, WorkflowState.ARCHIVED);
        allow(WorkflowState.ALREADY_APPLIED, WorkflowState.ARCHIVED);
        allow(WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT, WorkflowState.APPLICATION_PREPARING, WorkflowState.READY_FOR_APPROVAL, WorkflowState.APPROVED, WorkflowState.ARCHIVED);
    }

    private void allow(WorkflowState from, WorkflowState... to) {
        transitions.put(from, EnumSet.copyOf(List.of(to)));
    }

    public boolean canTransition(WorkflowState from, WorkflowState to) {
        if (from == to) return true;
        return transitions.getOrDefault(from, Set.of()).contains(to);
    }

    public void validate(WorkflowState from, WorkflowState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid application transition: " + from + " -> " + to);
        }
    }

    public Set<WorkflowState> allowedTransitions(WorkflowState from) {
        return Set.copyOf(transitions.getOrDefault(from, Set.of()));
    }
}
