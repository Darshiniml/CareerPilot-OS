package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import com.careerpilot.backend.modules.interview.repositories.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockInterviewTest {
    @Test
    void startsAndTracksMockInterviewSession() {
        InterviewSessionRepository sessionRepository = mock(InterviewSessionRepository.class);

        when(sessionRepository.save(any(InterviewSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockInterviewService service = new MockInterviewService(sessionRepository);
        InterviewSession session = service.startSession(UUID.randomUUID(), UUID.randomUUID(), InterviewType.TECHNICAL);

        // Mock finding the session during answer question
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));

        InterviewQuestion question = service.answerQuestion(session, "Question", "Answer", 120);

        assertThat(session.getSessionId()).isNotNull();
        assertThat(question.getQuestionText()).isEqualTo("Question");
        assertThat(session.getQuestions()).hasSize(1);
    }
}
