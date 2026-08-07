package com.careerpilot.backend.modules.interview.adapters.in.web;

import com.careerpilot.backend.modules.interview.domain.InterviewKnowledge;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.domain.InterviewType;
import com.careerpilot.backend.modules.interview.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interview")
@Tag(name = "Interview Intelligence", description = "Deterministic interview preparation APIs")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewPlannerService plannerService;
    private final QuestionGeneratorService questionGeneratorService;
    private final MockInterviewService mockInterviewService;
    private final AnswerEvaluatorService answerEvaluatorService;
    private final FeedbackEngineService feedbackEngineService;
    private final ReadinessService readinessService;
    private final LearningRecommendationService learningRecommendationService;

    @PostMapping("/prepare")
    @Operation(summary = "Prepare interview knowledge and plan")
    public ResponseEntity<InterviewKnowledge> prepare(@RequestBody PrepareRequest request) {
        InterviewKnowledge knowledge = plannerService.buildKnowledge(request.applicationId(), request.candidateId(), request.companyId(), request.jobId(), request.stage(), request.interviewType());
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledge);
    }

    @PostMapping("/questions")
    public ResponseEntity<List<Map<String, Object>>> questions(@RequestBody InterviewKnowledge knowledge) {
        return ResponseEntity.ok(questionGeneratorService.generateQuestions(knowledge));
    }

    @PostMapping("/start")
    public ResponseEntity<InterviewSession> start(@RequestBody StartRequest request) {
        InterviewSession session = mockInterviewService.startSession(request.applicationId(), request.candidateId(), request.interviewType());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/answer")
    public ResponseEntity<Map<String, Object>> answer(@RequestBody AnswerRequest request) {
        InterviewSession session = mockInterviewService.startSession(request.applicationId(), request.candidateId(), request.interviewType());
        var question = mockInterviewService.answerQuestion(session, request.question(), request.answer(), request.timeTakenSeconds());
        var evaluated = answerEvaluatorService.evaluate(question);
        return ResponseEntity.ok(Map.of("question", evaluated.getQuestionText(), "feedback", evaluated.getEvaluationFeedback()));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@RequestBody EvaluateRequest request) {
        return ResponseEntity.ok(Map.of("feedback", feedbackEngineService.buildFeedback(null)));
    }

    @GetMapping("/history")
    public ResponseEntity<List<InterviewSession>> history() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<InterviewSession> byId(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(InterviewSession.builder().sessionId(sessionId).build());
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        return ResponseEntity.ok(readinessService.calculateReadiness(0.78, 0.74, 0.72, 0.76, 0.69));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<List<Map<String, Object>>> recommendations(@RequestBody RecommendationRequest request) {
        return ResponseEntity.ok(learningRecommendationService.generateRecommendations(request.readinessScore()));
    }

    public record PrepareRequest(UUID applicationId, UUID candidateId, UUID companyId, UUID jobId, String stage, String interviewType) {}
    public record StartRequest(UUID applicationId, UUID candidateId, InterviewType interviewType) {}
    public record AnswerRequest(UUID applicationId, UUID candidateId, InterviewType interviewType, String question, String answer, int timeTakenSeconds) {}
    public record EvaluateRequest(UUID sessionId) {}
    public record RecommendationRequest(double readinessScore) {}
}
