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
        allow(WorkflowState.DISCOVERED, WorkflowState.MATCHED);
        allow(WorkflowState.MATCHED, WorkflowState.ELIGIBLE, WorkflowState.REJECTED);
        allow(WorkflowState.ELIGIBLE, WorkflowState.READY, WorkflowState.REJECTED);
        allow(WorkflowState.READY, WorkflowState.WAITING_APPROVAL, WorkflowState.APPROVED);
        allow(WorkflowState.WAITING_APPROVAL, WorkflowState.APPROVED, WorkflowState.REJECTED);
        allow(WorkflowState.APPROVED, WorkflowState.SUBMITTING);
        allow(WorkflowState.SUBMITTING, WorkflowState.SUBMITTED, WorkflowState.FAILED);
        allow(WorkflowState.FAILED, WorkflowState.RETRYING, WorkflowState.ARCHIVED);
        allow(WorkflowState.RETRYING, WorkflowState.SUBMITTING, WorkflowState.FAILED);
        allow(WorkflowState.SUBMITTED, WorkflowState.TRACKING, WorkflowState.COMPLETED, WorkflowState.WITHDRAWN);
        allow(WorkflowState.TRACKING, WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED_BY_COMPANY, WorkflowState.WITHDRAWN, WorkflowState.COMPLETED);
        allow(WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED_BY_COMPANY, WorkflowState.COMPLETED);
        allow(WorkflowState.OFFER, WorkflowState.COMPLETED, WorkflowState.WITHDRAWN);
        allow(WorkflowState.REJECTED, WorkflowState.ARCHIVED);
        allow(WorkflowState.REJECTED_BY_COMPANY, WorkflowState.ARCHIVED);
        allow(WorkflowState.WITHDRAWN, WorkflowState.ARCHIVED);
        allow(WorkflowState.COMPLETED, WorkflowState.ARCHIVED);
    }

    private void allow(WorkflowState from, WorkflowState... to) {
        transitions.put(from, EnumSet.copyOf(List.of(to)));
    }

    public boolean canTransition(WorkflowState from, WorkflowState to) {
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
