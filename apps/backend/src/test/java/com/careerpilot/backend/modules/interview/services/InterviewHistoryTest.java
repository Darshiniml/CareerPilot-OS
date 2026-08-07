package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewHistoryTest {
    @Test
    void preservesInterviewSessionHistory() {
        InterviewSession session = InterviewSession.builder().build();
        session.getQuestions().add(new com.careerpilot.backend.modules.interview.domain.InterviewQuestion());

        assertThat(session.getQuestions()).hasSize(1);
    }
}
