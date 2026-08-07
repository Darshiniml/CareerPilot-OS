package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewTest {
    @Test
    void startsAndTracksMockInterviewSession() {
        MockInterviewService service = new MockInterviewService();
        InterviewSession session = service.startSession(UUID.randomUUID(), UUID.randomUUID(), InterviewType.TECHNICAL);
        InterviewQuestion question = service.answerQuestion(session, "Question", "Answer", 120);

        assertThat(session.getSessionId()).isNotNull();
        assertThat(question.getQuestionText()).isEqualTo("Question");
        assertThat(session.getQuestions()).hasSize(1);
    }
}
