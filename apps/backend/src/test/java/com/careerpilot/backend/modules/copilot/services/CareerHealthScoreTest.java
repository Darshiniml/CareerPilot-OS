package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerHealthScoreTest {
    @Test
    void computesOverallHealthScore() {
        CareerHealthScoreService service = new CareerHealthScoreService();
        CopilotContext context = new CopilotContext();
        context.setResumeKnowledge(java.util.Map.of("quality", 0.8));
        context.setMatchingData(java.util.Map.of("overallScore", 0.75));
        context.setInterviewData(java.util.Map.of("readiness", 0.7));
        context.setLearningData(java.util.Map.of("progress", 0.8));
        context.setApplicationData(java.util.Map.of("successRate", 0.65));

        var score = service.calculate(context);
        assertThat(score.overallScore()).isGreaterThan(0.0);
        assertThat(score.categoryScores()).containsKey("resumeQuality");
    }
}
