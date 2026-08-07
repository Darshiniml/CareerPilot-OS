package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewKnowledge;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPlannerTest {
    @Test
    void buildsInterviewKnowledgeForApplication() {
        InterviewPlannerService service = new InterviewPlannerService();
        InterviewKnowledge knowledge = service.buildKnowledge(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "technical", "technical");

        assertThat(knowledge.getInterviewId()).isNotNull();
        assertThat(knowledge.getPreparationChecklist()).isNotEmpty();
    }
}
