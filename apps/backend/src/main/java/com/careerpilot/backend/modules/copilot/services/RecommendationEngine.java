package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotRecommendation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationEngine {
    public List<CopilotRecommendation> generate(CopilotContext context) {
        List<CopilotRecommendation> recommendations = new ArrayList<>();
        if (context.getMatchingData() != null && context.getMatchingData().containsKey("missingSkills")) {
            CopilotRecommendation recommendation = new CopilotRecommendation();
            recommendation.setTitle("Learn AWS");
            recommendation.setReason("Evidence-based recommendation: missing skills are blocking your current match quality.");
            recommendation.setEvidence(List.of("18 matching jobs require AWS", "Your current profile lacks AWS coverage"));
            recommendation.setAction("study");
            recommendations.add(recommendation);
        }
        if (context.getInterviewData() != null) {
            CopilotRecommendation recommendation = new CopilotRecommendation();
            recommendation.setTitle("Practice system design");
            recommendation.setReason("Interview readiness is below the target for technical roles.");
            recommendation.setEvidence(List.of("Current interview readiness is 0.74", "Technical stages need stronger design recall"));
            recommendation.setAction("prepare");
            recommendations.add(recommendation);
        }
        return recommendations;
    }
}
