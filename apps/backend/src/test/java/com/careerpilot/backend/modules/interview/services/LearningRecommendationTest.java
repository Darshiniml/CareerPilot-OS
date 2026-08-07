package com.careerpilot.backend.modules.interview.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LearningRecommendationTest {
    @Test
    void recommendsDeterministicLearningPlan() {
        LearningRecommendationService service = new LearningRecommendationService();
        List<Map<String, Object>> recommendations = service.generateRecommendations(0.65);

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0)).containsKey("focus");
    }
}
