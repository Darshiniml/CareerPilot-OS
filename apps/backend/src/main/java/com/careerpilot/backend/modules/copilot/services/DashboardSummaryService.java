package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardSummaryService {
    public Map<String, Object> buildSummary(CopilotContext context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("applicationsSubmitted", context.getApplicationData().getOrDefault("submittedThisWeek", 0));
        summary.put("averageMatchScore", context.getMatchingData().getOrDefault("averageScore", 0.0));
        summary.put("newMatchingJobs", 4);
        summary.put("interviewReadiness", context.getInterviewData().getOrDefault("readiness", 0.0));
        summary.put("learningProgress", context.getLearningData().getOrDefault("progress", 0.0));
        summary.put("topRecommendation", "Practice system design");
        return summary;
    }
}
