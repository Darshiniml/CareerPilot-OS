package com.careerpilot.backend.modules.interview.services;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReadinessService {

    public Map<String, Object> calculateReadiness(double technicalScore, double behavioralScore, double codingScore, double communicationScore, double systemDesignScore) {
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("overallReadiness", Math.round((technicalScore + behavioralScore + codingScore + communicationScore + systemDesignScore) / 5.0 * 100.0) / 100.0);
        readiness.put("technicalReadiness", technicalScore);
        readiness.put("behavioralReadiness", behavioralScore);
        readiness.put("codingReadiness", codingScore);
        readiness.put("communicationReadiness", communicationScore);
        readiness.put("systemDesignReadiness", systemDesignScore);
        readiness.put("explanations", Map.of(
                "technical", "Based on core domain knowledge and role fit",
                "behavioral", "Based on leadership and collaboration stories",
                "coding", "Based on practice and answer completeness",
                "communication", "Based on clarity and structure",
                "systemDesign", "Based on architecture fundamentals"
        ));
        return readiness;
    }
}
