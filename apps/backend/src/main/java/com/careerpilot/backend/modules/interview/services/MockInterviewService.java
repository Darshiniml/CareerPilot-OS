package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import com.careerpilot.backend.modules.interview.repositories.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MockInterviewService {

    private final InterviewSessionRepository sessionRepository;

    @Transactional
    public InterviewSession startSession(UUID applicationId, UUID candidateId, InterviewType interviewType) {
        InterviewSession session = InterviewSession.builder()
                .sessionId(UUID.randomUUID())
                .applicationId(applicationId)
                .candidateId(candidateId)
                .interviewType(interviewType)
                .startedAt(Instant.now())
                .overallReadiness(0.0)
                .build();
        return sessionRepository.save(session);
    }

    @Transactional
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

        // Ensure the session entity is managed or loaded from DB
        InterviewSession managedSession = sessionRepository.findById(session.getSessionId())
                .orElse(session);

        managedSession.getQuestions().add(question);

        // Re-calculate overall readiness based on scores
        double totalScore = 0;
        for (InterviewQuestion q : managedSession.getQuestions()) {
            totalScore += (q.getCorrectnessScore() + q.getCompletenessScore() + q.getTechnicalAccuracyScore()) / 3.0;
        }
        double avgReadiness = managedSession.getQuestions().isEmpty() ? 0.0 : totalScore / managedSession.getQuestions().size();
        managedSession.setOverallReadiness(avgReadiness);

        sessionRepository.save(managedSession);
        return question;
    }
}
