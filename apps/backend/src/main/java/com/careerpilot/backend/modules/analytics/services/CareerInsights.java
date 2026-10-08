package com.careerpilot.backend.modules.analytics.services;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CareerInsights {

    public List<Map<String, Object>> generateRecommendations(
            List<String> missingSkills,
            Map<String, Double> demandPercentages,
            Map<String, Object> interviewScores) {

        List<Map<String, Object>> recommendations = new ArrayList<>();

        // 1. Skill Demand Recommendations
        if (missingSkills != null && demandPercentages != null) {
            for (String skill : missingSkills) {
                double demand = demandPercentages.getOrDefault(skill.toLowerCase(), 0.0);
                if (demand >= 30.0) {
                    recommendations.add(Map.of(
                            "type", "SKILL_DEMAND",
                            "recommendation", "Learn " + capitalize(skill) + " to expand your job matching pool.",
                            "evidence", String.format("Learn %s because %.1f%% of your target jobs require it.", capitalize(skill), demand),
                            "skill", skill
                    ));
                }
            }
        }

        // 2. Interview Gap Recommendations
        if (interviewScores != null && !interviewScores.isEmpty()) {
            double correctness = (double) interviewScores.getOrDefault("correctness", 100.0);
            double technicalAccuracy = (double) interviewScores.getOrDefault("technicalAccuracy", 100.0);
            double communication = (double) interviewScores.getOrDefault("communication", 100.0);

            if (correctness < 75.0) {
                recommendations.add(Map.of(
                        "type", "INTERVIEW_GAP",
                        "recommendation", "Improve conceptual accuracy in technical questions.",
                        "evidence", String.format("Focus on answering core logic correctly; your average correctness score is %.1f%%.", correctness),
                        "skill", "interview-prep"
                ));
            }

            if (technicalAccuracy < 70.0) {
                recommendations.add(Map.of(
                        "type", "INTERVIEW_GAP",
                        "recommendation", "Practice writing precise technical answers.",
                        "evidence", String.format("Improve your technical accuracy which currently averages %.1f%% across sessions.", technicalAccuracy),
                        "skill", "system-design"
                ));
            }

            if (communication < 70.0) {
                recommendations.add(Map.of(
                        "type", "INTERVIEW_GAP",
                        "recommendation", "Practice structural communication techniques (STAR method).",
                        "evidence", String.format("Improve response structures because communication readiness is at %.1f%%.", communication),
                        "skill", "behavioral-prep"
                ));
            }
        }

        // No data, no recommendations: nothing is invented to fill the list.

        return recommendations;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
