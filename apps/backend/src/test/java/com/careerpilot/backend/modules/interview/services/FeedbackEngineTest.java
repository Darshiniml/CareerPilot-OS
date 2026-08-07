package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackEngineTest {
    @Test
    void buildsExplainableFeedback() {
        FeedbackEngineService service = new FeedbackEngineService();
        Map<String, Object> feedback = service.buildFeedback(InterviewSession.builder().build());

        assertThat(feedback).containsKey("strengths");
        assertThat(feedback.get("explainable")).isEqualTo(true);
    }
}
