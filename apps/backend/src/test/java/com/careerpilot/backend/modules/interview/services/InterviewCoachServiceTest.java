package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiProviderException;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.repositories.InterviewSessionRepository;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService.StartSessionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterviewCoachServiceTest {

    private InterviewSessionRepository sessionRepository;
    private ApplicationRecordRepository applicationRepository;
    private JobContextService jobContextService;
    private CandidateKnowledgeService candidateKnowledgeService;
    private AiGatewayClient gatewayClient;
    private InterviewCoachService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionRepository = mock(InterviewSessionRepository.class);
        applicationRepository = mock(ApplicationRecordRepository.class);
        jobContextService = mock(JobContextService.class);
        candidateKnowledgeService = mock(CandidateKnowledgeService.class);
        gatewayClient = mock(AiGatewayClient.class);
        service = new InterviewCoachService(sessionRepository, applicationRepository, jobContextService,
                candidateKnowledgeService, gatewayClient, com.careerpilot.backend.config.Transactions.inline());
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(i -> i.getArgument(0));
        when(candidateKnowledgeService.candidateSummary(userId)).thenReturn(Map.of("skills", List.of("Java")));
    }

    private static Map<String, Object> question(String text, String type, String skill) {
        return Map.of("question", text, "type", type, "skillArea", skill, "difficulty", "MEDIUM",
                "rationale", "r", "evaluationCriteria", List.of("covers X"), "provenance", "AI_GENERATED_PRACTICE_QUESTION");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startSessionUsesRealJobContextAndPersistsGeneratedQuestions() {
        UUID jobId = UUID.randomUUID();
        when(jobContextService.jobContext(jobId)).thenReturn(Map.of("title", "Backend Engineer", "company", "Acme"));
        when(gatewayClient.run(eq("INTERVIEW_QUESTIONS"), anyMap())).thenReturn(Map.of("questions", List.of(
                question("Explain Kafka consumer groups", "TECHNICAL", "Kafka"),
                question("Tell me about a conflict", "BEHAVIORAL", "Collaboration"))));

        InterviewSession session = service.startSession(userId,
                new StartSessionCommand(jobId, null, null, null, null, List.of("technical", "behavioral"), "hard", 2));

        assertEquals(userId, session.getCandidateId());
        assertEquals("Backend Engineer", session.getTargetRole());
        assertEquals("HARD", session.getDifficulty());
        assertEquals(2, session.getQuestions().size());
        assertEquals("AI_GENERATED_PRACTICE_QUESTION", session.getQuestions().get(0).getProvenance());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(gatewayClient).run(eq("INTERVIEW_QUESTIONS"), payload.capture());
        assertEquals(Map.of("title", "Backend Engineer", "company", "Acme"), payload.getValue().get("job"));
        assertEquals(List.of("TECHNICAL", "BEHAVIORAL"), payload.getValue().get("types"));
    }

    @Test
    void cannotStartSessionForAnotherUsersApplication() {
        UUID appId = UUID.randomUUID();
        ApplicationRecord foreign = new ApplicationRecord();
        foreign.setCandidateId(UUID.randomUUID());
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(foreign));
        assertThrows(NoSuchElementException.class, () -> service.startSession(userId,
                new StartSessionCommand(null, appId, null, null, null, null, null, null)));
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void answersAreEvaluatedByTheModelAndScoresPersisted() {
        InterviewSession session = sessionWithQuestion();
        UUID qid = session.getQuestions().get(0).getQuestionId();
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(gatewayClient.run(eq("INTERVIEW_EVALUATE"), anyMap())).thenReturn(Map.of(
                "scores", Map.of("technicalAccuracy", 0.6, "completeness", 0.5, "clarity", 0.8, "relevance", 0.9, "overall", 0.67),
                "feedback", "Mention partition rebalancing.", "strengths", List.of(), "improvements", List.of()));

        InterviewQuestion q = service.answer(userId, session.getSessionId(), qid, "Consumers in a group share partitions...", 90);

        assertEquals(0.67, q.getOverallScore());
        assertEquals(0.6, q.getTechnicalAccuracyScore());
        assertEquals("Mention partition rebalancing.", q.getEvaluationFeedback());
        assertNotNull(q.getAnsweredAt());
        assertEquals(0.67, session.getOverallReadiness());
        verify(gatewayClient).run(eq("INTERVIEW_EVALUATE"), argThat(p -> "Consumers in a group share partitions...".equals(p.get("answer"))));
    }

    @Test
    void aiFailureDuringEvaluationIsExplicitAndNothingIsScored() {
        InterviewSession session = sessionWithQuestion();
        InterviewQuestion question = session.getQuestions().get(0);
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        when(gatewayClient.run(eq("INTERVIEW_EVALUATE"), anyMap()))
                .thenThrow(new AiProviderException("model unavailable", "AI_PROVIDER_UNAVAILABLE", 503));

        assertThrows(AiProviderException.class,
                () -> service.answer(userId, session.getSessionId(), question.getQuestionId(), "my answer", 10));
        assertNull(question.getOverallScore());
        assertNull(question.getCandidateAnswer());
    }

    @Test
    void otherUsersCannotAnswerOrReadASession() {
        InterviewSession session = sessionWithQuestion();
        when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        UUID attacker = UUID.randomUUID();
        assertThrows(NoSuchElementException.class, () -> service.get(attacker, session.getSessionId()));
        assertThrows(NoSuchElementException.class, () -> service.answer(attacker, session.getSessionId(),
                session.getQuestions().get(0).getQuestionId(), "x", 1));
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void readinessIsUnavailableWithoutEnoughAnsweredQuestions() {
        when(sessionRepository.findByCandidateId(userId)).thenReturn(List.of(sessionWithQuestion()));
        Map<String, Object> readiness = service.readiness(userId);
        assertEquals(false, readiness.get("available"));
        assertFalse(readiness.containsKey("overallReadiness"), "no invented readiness value");
    }

    @Test
    void readinessIsTheMeanOfRealScores() {
        InterviewSession s = sessionWithQuestion();
        s.getQuestions().clear();
        double[] scores = {0.5, 0.7, 0.9};
        for (int i = 0; i < scores.length; i++) {
            s.getQuestions().add(InterviewQuestion.builder().questionId(UUID.randomUUID()).questionText("q" + i)
                    .questionType("TECHNICAL").skillArea(i == 0 ? "SQL" : "Java").overallScore(scores[i])
                    .createdAt(Instant.now()).build());
        }
        when(sessionRepository.findByCandidateId(userId)).thenReturn(List.of(s));
        Map<String, Object> readiness = service.readiness(userId);
        assertEquals(true, readiness.get("available"));
        assertEquals(0.7, (Double) readiness.get("overallReadiness"), 1e-9);
    }

    private InterviewSession sessionWithQuestion() {
        InterviewSession s = InterviewSession.builder()
                .sessionId(UUID.randomUUID()).candidateId(userId).status(InterviewSession.STATUS_ACTIVE)
                .difficulty("MEDIUM").startedAt(Instant.now()).build();
        s.getQuestions().add(InterviewQuestion.builder().questionId(UUID.randomUUID()).questionOrder(1)
                .questionText("Explain Kafka consumer groups").questionType("TECHNICAL").skillArea("Kafka")
                .evaluationCriteriaJson("[\"partitions\"]").createdAt(Instant.now()).build());
        return s;
    }
}
