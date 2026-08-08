package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MetricCalculator {

    public double calculateApplicationSuccessRate(List<ApplicationRecord> applications) {
        long submitted = applications.stream()
                .filter(r -> r.getSubmittedAt() != null || isSubmittedState(r.getWorkflowState()))
                .count();
        if (submitted == 0) return 0.0;

        long successful = applications.stream()
                .filter(r -> r.getSubmittedAt() != null && r.getWorkflowState() != WorkflowState.FAILED)
                .count();

        return ((double) successful / submitted) * 100.0;
    }

    public double calculateInterviewConversionRate(List<ApplicationRecord> applications) {
        long submitted = applications.stream()
                .filter(r -> r.getSubmittedAt() != null || isSubmittedState(r.getWorkflowState()))
                .count();
        if (submitted == 0) return 0.0;

        long interviews = applications.stream()
                .filter(r -> r.getWorkflowState() == WorkflowState.INTERVIEW || r.getWorkflowState() == WorkflowState.OFFER || r.getWorkflowState() == WorkflowState.COMPLETED)
                .count();

        return ((double) interviews / submitted) * 100.0;
    }

    public double calculateOfferConversionRate(List<ApplicationRecord> applications) {
        long submitted = applications.stream()
                .filter(r -> r.getSubmittedAt() != null || isSubmittedState(r.getWorkflowState()))
                .count();
        if (submitted == 0) return 0.0;

        long offers = applications.stream()
                .filter(r -> r.getWorkflowState() == WorkflowState.OFFER || r.getWorkflowState() == WorkflowState.COMPLETED)
                .count();

        return ((double) offers / submitted) * 100.0;
    }

    public double calculateAverageMatchScore(List<ApplicationRecord> applications) {
        return applications.stream()
                .filter(r -> r.getMatchScore() != null)
                .mapToDouble(ApplicationRecord::getMatchScore)
                .average()
                .orElse(0.0);
    }

    public double calculateAverageInterviewReadiness(List<InterviewSession> interviews) {
        return interviews.stream()
                .mapToDouble(InterviewSession::getOverallReadiness)
                .average()
                .orElse(0.0) * 100.0; // Normalized to 100 scale
    }

    public double calculateSkillCoverage(Set<String> candidateSkills, Set<String> targetSkills) {
        if (targetSkills == null || targetSkills.isEmpty()) return 100.0;
        long matchCount = candidateSkills.stream()
                .filter(skill -> targetSkills.contains(skill.toLowerCase()))
                .count();
        return ((double) matchCount / targetSkills.size()) * 100.0;
    }

    private boolean isSubmittedState(WorkflowState state) {
        return state == WorkflowState.SUBMITTED || state == WorkflowState.TRACKING || 
               state == WorkflowState.INTERVIEW || state == WorkflowState.OFFER || 
               state == WorkflowState.COMPLETED || state == WorkflowState.FAILED ||
               state == WorkflowState.REJECTED_BY_COMPANY;
    }
}
