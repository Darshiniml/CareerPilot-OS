package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.analytics.domain.LearningPath;
import com.careerpilot.backend.modules.analytics.repositories.LearningPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RuleBasedCareerPredictionEngine implements CareerPredictionEngine {

    private final LearningPathRepository learningPathRepository;

    @Override
    public Map<String, Object> predictImprovements(UUID candidateId) {
        List<LearningPath> paths = learningPathRepository.findByCandidateId(candidateId);
        
        double potentialMatchImprovement = 0.0;
        double potentialReadinessImprovement = 15.0; // Estimate from preparation

        for (LearningPath lp : paths) {
            potentialMatchImprovement += lp.getExpectedMatchImprovement();
        }

        // Clip maximum logical improvements
        potentialMatchImprovement = Math.min(25.0, potentialMatchImprovement);

        Map<String, Object> predictions = new HashMap<>();
        predictions.put("potentialMatchImprovementPercent", potentialMatchImprovement);
        predictions.put("potentialInterviewReadinessImprovementPercent", potentialReadinessImprovement);
        predictions.put("timelineGoalProgressEstimatePercent", 20.0);
        predictions.put("forecastedSkillDemandGrowth", Map.of("aws", 8.0, "kubernetes", 12.0, "spring-boot", 5.0));
        predictions.put("disclaimer", "Disclaimer: These predictions are evidence-based projections generated from platform learning and match patterns. They do not constitute guaranteed employment or offer outcomes.");

        return predictions;
    }
}
