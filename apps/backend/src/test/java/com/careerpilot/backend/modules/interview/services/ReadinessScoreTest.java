package com.careerpilot.backend.modules.interview.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessScoreTest {
    @Test
    void computesOverallAndComponentReadinessScores() {
        ReadinessService service = new ReadinessService();
        Map<String, Object> readiness = service.calculateReadiness(0.8, 0.7, 0.75, 0.8, 0.7);

        assertThat(readiness.get("overallReadiness")).isEqualTo(0.75);
        assertThat(readiness).containsKey("technicalReadiness");
    }
}
