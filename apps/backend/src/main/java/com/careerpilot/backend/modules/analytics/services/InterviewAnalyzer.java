package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class InterviewAnalyzer {

    private final MetricCalculator metricCalculator;

    public Map<String, Object> analyzeInterviews(List<InterviewSession> interviews) {
        long totalSessions = interviews.size();
        double averageReadiness = metricCalculator.calculateAverageInterviewReadiness(interviews);

        // Average scores across all questions in all sessions
        double correctnessSum = 0;
        double completenessSum = 0;
        double accuracySum = 0;
        double communicationSum = 0;
        double examplesSum = 0;
        double confidenceSum = 0;
        double structureSum = 0;
        int totalQuestions = 0;

        Map<String, Integer> typeCount = new HashMap<>();

        for (InterviewSession s : interviews) {
            String type = s.getInterviewType() != null ? s.getInterviewType().name() : "GENERAL";
            typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);

            for (InterviewQuestion q : s.getQuestions()) {
                correctnessSum += q.getCorrectnessScore() != null ? q.getCorrectnessScore() : 0.0;
                completenessSum += q.getCompletenessScore() != null ? q.getCompletenessScore() : 0.0;
                accuracySum += q.getTechnicalAccuracyScore() != null ? q.getTechnicalAccuracyScore() : 0.0;
                communicationSum += q.getCommunicationScore() != null ? q.getCommunicationScore() : 0.0;
                examplesSum += q.getExamplesScore() != null ? q.getExamplesScore() : 0.0;
                confidenceSum += q.getConfidenceScore() != null ? q.getConfidenceScore() : 0.0;
                structureSum += q.getStructureScore() != null ? q.getStructureScore() : 0.0;
                totalQuestions++;
            }
        }

        Map<String, Object> averageScores = new HashMap<>();
        if (totalQuestions > 0) {
            averageScores.put("correctness", (correctnessSum / totalQuestions) * 100.0);
            averageScores.put("completeness", (completenessSum / totalQuestions) * 100.0);
            averageScores.put("technicalAccuracy", (accuracySum / totalQuestions) * 100.0);
            averageScores.put("communication", (communicationSum / totalQuestions) * 100.0);
            averageScores.put("examples", (examplesSum / totalQuestions) * 100.0);
            averageScores.put("confidence", (confidenceSum / totalQuestions) * 100.0);
            averageScores.put("structure", (structureSum / totalQuestions) * 100.0);
        } else {
            averageScores.put("correctness", 0.0);
            averageScores.put("completeness", 0.0);
            averageScores.put("technicalAccuracy", 0.0);
            averageScores.put("communication", 0.0);
            averageScores.put("examples", 0.0);
            averageScores.put("confidence", 0.0);
            averageScores.put("structure", 0.0);
        }

        // Calculate readiness improvement
        double readinessImprovement = 0.0;
        if (interviews.size() >= 2) {
            double latest = interviews.get(interviews.size() - 1).getOverallReadiness();
            double oldest = interviews.get(0).getOverallReadiness();
            readinessImprovement = (latest - oldest) * 100.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalSessions", totalSessions);
        result.put("averageReadiness", averageReadiness);
        result.put("readinessImprovement", readinessImprovement);
        result.put("averageQuestionScores", averageScores);
        result.put("sessionTypeDistribution", typeCount);

        return result;
    }
}
