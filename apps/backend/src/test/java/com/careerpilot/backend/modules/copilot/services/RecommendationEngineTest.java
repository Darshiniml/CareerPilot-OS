package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEngineTest {
    @Test
    void recommendsBasedOnEvidence() {
        RecommendationEngine engine = new RecommendationEngine();
        CopilotContext context = new CopilotContext();
        context.setIntent(CopilotIntent.GAP_ANALYSIS);
        context.setMatchingData(java.util.Map.of("overallScore", 0.62, "missingSkills", java.util.List.of("aws")));

        var recommendations = engine.generate(context);
        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).getReason()).contains("evidence");
    }
}
