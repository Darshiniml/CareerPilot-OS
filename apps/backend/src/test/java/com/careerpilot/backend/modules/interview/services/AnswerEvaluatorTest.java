package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerEvaluatorTest {
    @Test
    void evaluatesAnswerWithExplainableScores() {
        InterviewQuestion question = InterviewQuestion.builder()
                .correctnessScore(0.9)
                .completenessScore(0.8)
                .technicalAccuracyScore(0.7)
                .communicationScore(0.8)
                .examplesScore(0.6)
                .confidenceScore(0.7)
                .structureScore(0.9)
                .build();

        AnswerEvaluatorService service = new AnswerEvaluatorService();
        InterviewQuestion evaluated = service.evaluate(question);

        assertThat(evaluated.getEvaluationFeedback()).contains("Correctness");
        assertThat(evaluated.getEvaluationFeedback()).contains("communication");
    }
}
