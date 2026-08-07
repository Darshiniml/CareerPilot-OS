package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewKnowledge;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTest {
    @Test
    void preparesInterviewAndGeneratesQuestions() {
        InterviewPlannerService planner = new InterviewPlannerService();
        QuestionGeneratorService generator = new QuestionGeneratorService();

        InterviewKnowledge knowledge = planner.buildKnowledge(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "technical", "technical");
        var questions = generator.generateQuestions(knowledge);

        assertThat(knowledge.getInterviewId()).isNotNull();
        assertThat(questions).isNotEmpty();
    }
}
