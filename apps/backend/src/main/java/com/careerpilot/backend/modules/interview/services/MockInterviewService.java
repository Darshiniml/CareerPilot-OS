package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class MockInterviewService {

    public InterviewSession startSession(UUID applicationId, UUID candidateId, InterviewType interviewType) {
        return InterviewSession.builder()
                .sessionId(UUID.randomUUID())
                .applicationId(applicationId)
                .candidateId(candidateId)
                .interviewType(interviewType)
                .startedAt(Instant.now())
                .overallReadiness(0.0)
                .build();
    }

    public InterviewQuestion answerQuestion(InterviewSession session, String questionText, String answer, int timeTakenSeconds) {
        InterviewQuestion question = InterviewQuestion.builder()
                .questionId(UUID.randomUUID())
                .questionText(questionText)
                .candidateAnswer(answer)
                .timeTakenSeconds(timeTakenSeconds)
                .correctnessScore(0.8)
                .completenessScore(0.75)
                .technicalAccuracyScore(0.8)
                .communicationScore(0.7)
                .examplesScore(0.6)
                .confidenceScore(0.65)
                .structureScore(0.78)
                .evaluationFeedback("Answer was structured and relevant")
                .createdAt(Instant.now())
                .build();
        session.getQuestions().add(question);
        return question;
    }
}
