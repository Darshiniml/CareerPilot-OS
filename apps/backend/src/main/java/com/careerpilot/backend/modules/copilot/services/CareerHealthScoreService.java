package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CareerHealthScoreService {
    public ScoreResult calculate(CopilotContext context) {
        double resumeQuality = ((Number) context.getResumeKnowledge().getOrDefault("quality", 0.7)).doubleValue();
        double skillCoverage = ((Number) context.getMatchingData().getOrDefault("overallScore", 0.7)).doubleValue();
        double matchRate = ((Number) context.getMatchingData().getOrDefault("overallScore", 0.7)).doubleValue();
        double interviewReadiness = ((Number) context.getInterviewData().getOrDefault("readiness", 0.7)).doubleValue();
        double learningProgress = ((Number) context.getLearningData().getOrDefault("progress", 0.7)).doubleValue();
        double applicationSuccess = ((Number) context.getApplicationData().getOrDefault("successRate", 0.7)).doubleValue();

        double overall = (resumeQuality + skillCoverage + matchRate + interviewReadiness + learningProgress + applicationSuccess) / 6.0;
        Map<String, Double> categoryScores = new LinkedHashMap<>();
        categoryScores.put("resumeQuality", resumeQuality);
        categoryScores.put("skillCoverage", skillCoverage);
        categoryScores.put("matchRate", matchRate);
        categoryScores.put("interviewReadiness", interviewReadiness);
        categoryScores.put("learningProgress", learningProgress);
        categoryScores.put("applicationSuccess", applicationSuccess);
        return new ScoreResult(overall, categoryScores);
    }

    public record ScoreResult(double overallScore, Map<String, Double> categoryScores) {}
}
