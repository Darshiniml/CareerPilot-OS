package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotRecommendation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExplanationEngineTest {
    @Test
    void explainsRecommendationsWithEvidence() {
        ExplanationEngine engine = new ExplanationEngine();
        CopilotRecommendation recommendation = new CopilotRecommendation();
        recommendation.setTitle("Learn AWS");
        recommendation.setReason("72% of backend jobs require AWS");
        recommendation.setEvidence(java.util.List.of("18 matching jobs require AWS"));

        var explanation = engine.explain(recommendation);
        assertThat(explanation).contains("Evidence");
    }
}
