package com.careerpilot.backend.modules.interview.services;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningRecommendationService {

    public List<Map<String, Object>> generateRecommendations(double readinessScore) {
        List<Map<String, Object>> recommendations = new java.util.ArrayList<>();
        if (readinessScore < 0.7) {
            recommendations.add(Map.of("week", 1, "focus", "SQL joins and transactions", "expectedImpact", "Improves technical readiness"));
            recommendations.add(Map.of("week", 2, "focus", "Spring Security and JWT", "expectedImpact", "Improves backend readiness"));
            recommendations.add(Map.of("week", 3, "focus", "Docker and Kubernetes", "expectedImpact", "Improves deployment readiness"));
            recommendations.add(Map.of("week", 4, "focus", "System design fundamentals", "expectedImpact", "Improves architecture readiness"));
        } else {
            recommendations.add(Map.of("week", 1, "focus", "Behavioral storytelling", "expectedImpact", "Improves communication readiness"));
        }
        return recommendations;
    }
}
