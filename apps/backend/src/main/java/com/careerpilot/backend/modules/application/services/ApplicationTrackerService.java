package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.TrackingStatus;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationTrackerService {

    private final Map<WorkflowState, Set<TrackingStatus>> transitions = new EnumMap<>(WorkflowState.class);

    public ApplicationTrackerService() {
        transitions.put(WorkflowState.SUBMITTED, Set.of(TrackingStatus.APPLICATION_SUBMITTED, TrackingStatus.APPLICATION_VIEWED));
        transitions.put(WorkflowState.TRACKING, Set.of(TrackingStatus.UNDER_REVIEW, TrackingStatus.ASSESSMENT));
        transitions.put(WorkflowState.INTERVIEW, Set.of(TrackingStatus.INTERVIEW_SCHEDULED));
        transitions.put(WorkflowState.OFFER, Set.of(TrackingStatus.OFFER));
        transitions.put(WorkflowState.REJECTED_BY_COMPANY, Set.of(TrackingStatus.REJECTED));
        transitions.put(WorkflowState.WITHDRAWN, Set.of(TrackingStatus.WITHDRAWN));
        transitions.put(WorkflowState.COMPLETED, Set.of(TrackingStatus.COMPLETED));
    }

    public Set<TrackingStatus> allowedTrackingStatuses(WorkflowState workflowState) {
        return transitions.getOrDefault(workflowState, Set.of());
    }

    public TrackingStatus deriveStatus(ApplicationRecord application) {
        if (application.getWorkflowState() == WorkflowState.INTERVIEW) {
            return TrackingStatus.INTERVIEW_SCHEDULED;
        }
        if (application.getWorkflowState() == WorkflowState.OFFER) {
            return TrackingStatus.OFFER;
        }
        if (application.getWorkflowState() == WorkflowState.REJECTED_BY_COMPANY || application.getWorkflowState() == WorkflowState.REJECTED) {
            return TrackingStatus.REJECTED;
        }
        if (application.getWorkflowState() == WorkflowState.WITHDRAWN) {
            return TrackingStatus.WITHDRAWN;
        }
        if (application.getWorkflowState() == WorkflowState.COMPLETED) {
            return TrackingStatus.COMPLETED;
        }
        if (application.getWorkflowState() == WorkflowState.TRACKING) {
            return TrackingStatus.UNDER_REVIEW;
        }
        if (application.getWorkflowState() == WorkflowState.SUBMITTED) {
            return TrackingStatus.APPLICATION_SUBMITTED;
        }
        return TrackingStatus.APPLICATION_VIEWED;
    }
}
