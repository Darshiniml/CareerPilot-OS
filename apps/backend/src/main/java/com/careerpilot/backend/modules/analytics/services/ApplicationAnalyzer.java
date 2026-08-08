package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApplicationAnalyzer {

    private final MetricCalculator metricCalculator;

    public Map<String, Object> analyzeApplications(List<ApplicationRecord> applications) {
        long total = applications.size();
        long submitted = applications.stream()
                .filter(r -> r.getSubmittedAt() != null || isSubmittedState(r.getWorkflowState()))
                .count();

        long successful = applications.stream()
                .filter(r -> r.getSubmittedAt() != null && r.getWorkflowState() != WorkflowState.FAILED)
                .count();

        long rejected = applications.stream()
                .filter(r -> r.getWorkflowState() == WorkflowState.REJECTED || r.getWorkflowState() == WorkflowState.REJECTED_BY_COMPANY)
                .count();

        double submissionSuccessRate = metricCalculator.calculateApplicationSuccessRate(applications);
        double interviewConversionRate = metricCalculator.calculateInterviewConversionRate(applications);
        double offerConversionRate = metricCalculator.calculateOfferConversionRate(applications);
        double averageMatchScore = metricCalculator.calculateAverageMatchScore(applications);

        // Average Time to Apply
        double avgTimeToApplyMinutes = applications.stream()
                .filter(r -> r.getSubmittedAt() != null && r.getCreatedAt() != null)
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getSubmittedAt()).toMinutes())
                .average()
                .orElse(0.0);

        // Connector success rate
        long failed = applications.stream()
                .filter(r -> r.getWorkflowState() == WorkflowState.FAILED)
                .count();
        double connectorSuccessRate = total > 0 ? ((double) (total - failed) / total) * 100.0 : 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalApplications", total);
        result.put("submitted", submitted);
        result.put("successfulSubmissions", successful);
        result.put("rejected", rejected);
        result.put("submissionSuccessRate", submissionSuccessRate);
        result.put("interviewConversionRate", interviewConversionRate);
        result.put("offerConversionRate", offerConversionRate);
        result.put("averageMatchScore", averageMatchScore);
        result.put("averageTimeToApplyMinutes", avgTimeToApplyMinutes);
        result.put("connectorSuccessRate", connectorSuccessRate);

        return result;
    }

    private boolean isSubmittedState(WorkflowState state) {
        return state == WorkflowState.SUBMITTED || state == WorkflowState.TRACKING || 
               state == WorkflowState.INTERVIEW || state == WorkflowState.OFFER || 
               state == WorkflowState.COMPLETED || state == WorkflowState.FAILED ||
               state == WorkflowState.REJECTED_BY_COMPANY;
    }
}
