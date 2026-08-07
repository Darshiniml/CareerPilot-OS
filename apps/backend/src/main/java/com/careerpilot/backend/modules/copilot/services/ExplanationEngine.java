package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotRecommendation;
import org.springframework.stereotype.Service;

@Service
public class ExplanationEngine {
    public String explain(CopilotRecommendation recommendation) {
        return "Explanation: " + recommendation.getReason() + " Evidence: " + String.join(", ", recommendation.getEvidence());
    }
}
