package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.config.Transactions;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import com.careerpilot.backend.modules.interview.repositories.InterviewSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI interview coach. Questions are generated from the real job and the candidate's real profile,
 * answers are evaluated by the model with a rubric, and every score/summary is persisted. Readiness
 * is computed only from answered questions; without data it is reported as unavailable.
 *
 * <p>Methods that call the model are not transactional; results are written in a short
 * transaction afterwards so no DB connection is held while the model runs.
 */
@Service
@RequiredArgsConstructor
public class InterviewCoachService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> AI_TYPES = Set.of("TECHNICAL", "BEHAVIORAL", "SYSTEM_DESIGN", "CODING", "COMPANY");

    private final InterviewSessionRepository sessionRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final JobContextService jobContextService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final AiGatewayClient gatewayClient;
    private final Transactions transactions;

    public record StartSessionCommand(UUID jobId, UUID applicationId, String targetRole, String targetCompany,
                                      String jobDescription, List<String> questionTypes, String difficulty,
                                      Integer questionCount) {
    }

    @SuppressWarnings("unchecked")
    public InterviewSession startSession(UUID userId, StartSessionCommand cmd) {
        UUID jobId = cmd.jobId();
        UUID applicationId = cmd.applicationId();
        if (applicationId != null) {
            ApplicationRecord application = applicationRepository.findById(applicationId)
                    .filter(a -> userId.equals(a.getCandidateId()))
                    .orElseThrow(() -> new NoSuchElementException("Application not found"));
            jobId = application.getJobId();
        }

        Map<String, Object> job = new LinkedHashMap<>();
        String role;
        String company;
        if (jobId != null) {
            job.putAll(jobContextService.jobContext(jobId));
            role = Objects.toString(job.get("title"), null);
            company = Objects.toString(job.get("company"), null);
        } else {
            if (cmd.targetRole() == null || cmd.targetRole().isBlank()) {
                throw new IllegalArgumentException("Choose a job, an application, or enter a target role");
            }
            role = cmd.targetRole().trim();
            company = cmd.targetCompany() != null && !cmd.targetCompany().isBlank() ? cmd.targetCompany().trim() : null;
            job.put("title", role);
            if (company != null) {
                job.put("company", company);
            }
            if (cmd.jobDescription() != null && !cmd.jobDescription().isBlank()) {
                job.put("description", cmd.jobDescription().length() > 12000
                        ? cmd.jobDescription().substring(0, 12000) : cmd.jobDescription());
            }
            job.put("provenance", "entered by the candidate");
        }

        List<String> types = (cmd.questionTypes() == null || cmd.questionTypes().isEmpty()
                ? List.of("TECHNICAL", "BEHAVIORAL") : cmd.questionTypes()).stream()
                .map(t -> t.toUpperCase(Locale.ROOT)).filter(AI_TYPES::contains).distinct().toList();
        if (types.isEmpty()) {
            throw new IllegalArgumentException("questionTypes must include TECHNICAL, BEHAVIORAL, SYSTEM_DESIGN or CODING");
        }
        String difficulty = Optional.ofNullable(cmd.difficulty()).map(d -> d.toUpperCase(Locale.ROOT))
                .filter(d -> Set.of("EASY", "MEDIUM", "HARD").contains(d)).orElse("MEDIUM");
        int count = Math.max(1, Math.min(cmd.questionCount() == null ? 5 : cmd.questionCount(), 10));

        Map<String, Object> payload = new HashMap<>();
        payload.put("job", job);
        payload.put("candidate", candidateKnowledgeService.candidateSummary(userId));
        payload.put("types", types);
        payload.put("difficulty", difficulty);
        payload.put("count", count);
        Map<String, Object> previous = previousPerformance(userId);
        if (!previous.isEmpty()) {
            payload.put("previousPerformance", previous);
        }

        Map<String, Object> result = gatewayClient.run("INTERVIEW_QUESTIONS", payload);
        List<Map<String, Object>> generated = (List<Map<String, Object>>) result.getOrDefault("questions", List.of());
        if (generated.isEmpty()) {
            throw new IllegalStateException("The AI did not return any questions; please try again");
        }

        Instant now = Instant.now();
        InterviewSession session = InterviewSession.builder()
                .sessionId(UUID.randomUUID())
                .candidateId(userId)
                .applicationId(applicationId)
                .jobId(jobId)
                .interviewType(types.size() == 1 && !"COMPANY".equals(types.get(0)) ? InterviewType.valueOf(types.get(0)) : InterviewType.TECHNICAL)
                .status(InterviewSession.STATUS_ACTIVE)
                .difficulty(difficulty)
                .targetRole(truncate(role, 255))
                .targetCompany(truncate(company, 255))
                .startedAt(now)
                .overallReadiness(0.0)
                .build();
        int order = 1;
        for (Map<String, Object> q : generated) {
            String text = Objects.toString(q.get("question"), "").trim();
            if (text.isEmpty()) {
                continue;
            }
            session.getQuestions().add(InterviewQuestion.builder()
                    .questionId(UUID.randomUUID())
                    .questionOrder(order++)
                    .questionText(text)
                    .questionType(Objects.toString(q.get("type"), null))
                    .category(Objects.toString(q.get("type"), null))
                    .skillArea(truncate(Objects.toString(q.get("skillArea"), null), 255))
                    .difficulty(Objects.toString(q.get("difficulty"), difficulty))
                    .rationale(Objects.toString(q.get("rationale"), null))
                    .evaluationCriteriaJson(toJson(q.getOrDefault("evaluationCriteria", List.of())))
                    .provenance(Objects.toString(q.get("provenance"), "AI_GENERATED_PRACTICE_QUESTION"))
                    .createdAt(now)
                    .build());
        }
        return sessionRepository.save(session);
    }

    @SuppressWarnings("unchecked")
    public InterviewQuestion answer(UUID userId, UUID sessionId, UUID questionId, String answer, Integer timeTakenSeconds) {
        InterviewSession session = ownedSession(userId, sessionId);
        if (InterviewSession.STATUS_COMPLETED.equals(session.getStatus())) {
            throw new IllegalStateException("This session is already completed");
        }
        InterviewQuestion question = session.getQuestions().stream()
                .filter(q -> q.getQuestionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Question not found in this session"));
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("Answer must not be empty");
        }
        if (answer.length() > 12000) {
            throw new IllegalArgumentException("Answer is too long (max 12000 characters)");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("question", question.getQuestionText());
        payload.put("answer", answer);
        payload.put("skillArea", question.getSkillArea());
        payload.put("difficulty", question.getDifficulty());
        payload.put("evaluationCriteria", fromJson(question.getEvaluationCriteriaJson()));
        Map<String, Object> evaluation = gatewayClient.run("INTERVIEW_EVALUATE", payload);
        Map<String, Object> scores = (Map<String, Object>) evaluation.getOrDefault("scores", Map.of());

        return transactions.run(() -> {
            // re-read: the session may have changed while the model was running
            InterviewSession current = ownedSession(userId, sessionId);
            if (InterviewSession.STATUS_COMPLETED.equals(current.getStatus())) {
                throw new IllegalStateException("This session is already completed");
            }
            InterviewQuestion target = current.getQuestions().stream()
                    .filter(q -> q.getQuestionId().equals(questionId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Question not found in this session"));
            applyEvaluation(target, answer, timeTakenSeconds, evaluation, scores);
            current.setOverallReadiness(averageOverall(current));
            sessionRepository.save(current);
            return target;
        });
    }

    private static void applyEvaluation(InterviewQuestion question, String answer, Integer timeTakenSeconds,
                                        Map<String, Object> evaluation, Map<String, Object> scores) {
        question.setCandidateAnswer(answer);
        question.setTimeTakenSeconds(timeTakenSeconds);
        question.setAnsweredAt(Instant.now());
        question.setTechnicalAccuracyScore(number(scores.get("technicalAccuracy")));
        question.setCompletenessScore(number(scores.get("completeness")));
        question.setClarityScore(number(scores.get("clarity")));
        question.setCommunicationScore(number(scores.get("clarity")));
        question.setRelevanceScore(number(scores.get("relevance")));
        question.setOverallScore(number(scores.get("overall")));
        question.setCorrectnessScore(number(scores.get("technicalAccuracy")));
        question.setEvaluationFeedback(Objects.toString(evaluation.get("feedback"), null));
        question.setEvaluationJson(toJson(evaluation));
    }

    public InterviewSession complete(UUID userId, UUID sessionId) {
        InterviewSession session = ownedSession(userId, sessionId);
        List<Map<String, Object>> answered = new ArrayList<>();
        for (InterviewQuestion q : session.getQuestions()) {
            if (q.getCandidateAnswer() == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", q.getQuestionText());
            item.put("type", q.getQuestionType());
            item.put("skillArea", q.getSkillArea());
            item.put("overallScore", q.getOverallScore());
            item.put("feedback", q.getEvaluationFeedback());
            answered.add(item);
        }
        if (answered.isEmpty()) {
            throw new IllegalStateException("Answer at least one question before completing the session");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("session", Map.of("role", Objects.toString(session.getTargetRole(), ""),
                "company", Objects.toString(session.getTargetCompany(), ""), "difficulty", session.getDifficulty()));
        payload.put("answers", answered);
        Map<String, Object> feedback = gatewayClient.run("INTERVIEW_FEEDBACK", payload);
        return transactions.run(() -> {
            InterviewSession current = ownedSession(userId, sessionId);
            current.setSummaryJson(toJson(feedback));
            current.setOverallReadiness(averageOverall(current));
            current.setStatus(InterviewSession.STATUS_COMPLETED);
            current.setCompletedAt(Instant.now());
            return sessionRepository.save(current);
        });
    }

    @Transactional(readOnly = true)
    public InterviewSession get(UUID userId, UUID sessionId) {
        return ownedSession(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<InterviewSession> history(UUID userId) {
        return sessionRepository.findByCandidateId(userId).stream()
                .sorted(Comparator.comparing(InterviewSession::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Readiness from real answered questions only; "not enough data" otherwise. */
    @Transactional(readOnly = true)
    public Map<String, Object> readiness(UUID userId) {
        List<InterviewQuestion> answered = sessionRepository.findByCandidateId(userId).stream()
                .flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getOverallScore() != null)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answeredQuestions", answered.size());
        if (answered.size() < 3) {
            result.put("available", false);
            result.put("message", "Not enough data: answer at least 3 practice questions to estimate readiness");
            return result;
        }
        result.put("available", true);
        result.put("overallReadiness", round(answered.stream().mapToDouble(InterviewQuestion::getOverallScore).average().orElse(0)));
        Map<String, Double> byType = answered.stream()
                .filter(q -> q.getQuestionType() != null)
                .collect(Collectors.groupingBy(InterviewQuestion::getQuestionType,
                        Collectors.averagingDouble(InterviewQuestion::getOverallScore)));
        Map<String, Double> rounded = new TreeMap<>();
        byType.forEach((k, v) -> rounded.put(k, round(v)));
        result.put("byQuestionType", rounded);
        Map<String, Object> areas = skillAreaScores(answered);
        result.put("strongestAreas", areas.get("strongest"));
        result.put("weakestAreas", areas.get("weakest"));
        result.put("method", "mean of AI rubric scores across all answered practice questions");
        return result;
    }

    private Map<String, Object> previousPerformance(UUID userId) {
        List<InterviewQuestion> answered = sessionRepository.findByCandidateId(userId).stream()
                .flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getOverallScore() != null && q.getSkillArea() != null)
                .toList();
        if (answered.isEmpty()) {
            return Map.of();
        }
        return skillAreaScores(answered);
    }

    private static Map<String, Object> skillAreaScores(List<InterviewQuestion> answered) {
        Map<String, Double> bySkill = answered.stream()
                .filter(q -> q.getSkillArea() != null)
                .collect(Collectors.groupingBy(InterviewQuestion::getSkillArea,
                        Collectors.averagingDouble(InterviewQuestion::getOverallScore)));
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(bySkill.entrySet());
        sorted.sort(Map.Entry.comparingByValue());
        List<Map<String, Object>> weakest = sorted.stream().limit(3)
                .map(e -> Map.<String, Object>of("skillArea", e.getKey(), "score", round(e.getValue()))).toList();
        List<Map<String, Object>> strongest = sorted.stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3).map(e -> Map.<String, Object>of("skillArea", e.getKey(), "score", round(e.getValue()))).toList();
        return Map.of("weakest", weakest, "strongest", strongest);
    }

    private InterviewSession ownedSession(UUID userId, UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> userId.equals(s.getCandidateId()))
                .orElseThrow(() -> new NoSuchElementException("Interview session not found"));
    }

    private static double averageOverall(InterviewSession session) {
        return round(session.getQuestions().stream()
                .map(InterviewQuestion::getOverallScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    private static Double number(Object raw) {
        return raw instanceof Number n ? n.doubleValue() : null;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Object>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
