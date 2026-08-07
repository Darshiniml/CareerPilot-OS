package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewKnowledge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionGeneratorTest {
    @Test
    void generatesRoleSpecificQuestions() {
        InterviewKnowledge knowledge = InterviewKnowledge.builder()
                .requiredTechnologies(List.of("java"))
                .behavioralTopics(List.of("leadership"))
                .codingTopics(List.of("arrays"))
                .build();

        QuestionGeneratorService service = new QuestionGeneratorService();
        List<Map<String, Object>> questions = service.generateQuestions(knowledge);

        assertThat(questions).isNotEmpty();
        assertThat(questions).extracting("category").contains("Technical", "Behavioral", "Coding");
    }
}
