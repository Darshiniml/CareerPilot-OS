package com.careerpilot.backend.modules.interview.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService.StartSessionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** AI interview coach. Every session belongs to the authenticated candidate. */
@RestController
@RequestMapping("/api/v1/interview")
@Tag(name = "Interview Coach", description = "AI-generated practice interviews with rubric-based answer evaluation")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewCoachService coachService;
    private final CurrentUser currentUser;

    @PostMapping("/sessions")
    @Operation(summary = "Start a practice interview generated from a job (or a target role) and your profile")
    public ResponseEntity<InterviewSession> start(@RequestBody StartRequest request, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        InterviewSession session = coachService.startSession(userId, new StartSessionCommand(
                request.jobId(), request.applicationId(), request.targetRole(), request.targetCompany(),
                request.jobDescription(), request.questionTypes(), request.difficulty(), request.questionCount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/sessions/{sessionId}/answers")
    @Operation(summary = "Submit an answer; the AI evaluates it against the question's criteria")
    public ResponseEntity<InterviewQuestion> answer(@PathVariable UUID sessionId, @RequestBody AnswerRequest request,
                                                    Principal principal) {
        UUID userId = currentUser.requireId(principal);
        return ResponseEntity.ok(coachService.answer(userId, sessionId, request.questionId(), request.answer(),
                request.timeTakenSeconds()));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    @Operation(summary = "Finish the session and get AI feedback across all answers")
    public ResponseEntity<InterviewSession> complete(@PathVariable UUID sessionId, Principal principal) {
        return ResponseEntity.ok(coachService.complete(currentUser.requireId(principal), sessionId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<InterviewSession> get(@PathVariable UUID sessionId, Principal principal) {
        return ResponseEntity.ok(coachService.get(currentUser.requireId(principal), sessionId));
    }

    @GetMapping({"/sessions", "/history"})
    public ResponseEntity<List<InterviewSession>> history(Principal principal) {
        return ResponseEntity.ok(coachService.history(currentUser.requireId(principal)));
    }

    @GetMapping("/readiness")
    @Operation(summary = "Interview readiness computed from your answered practice questions")
    public ResponseEntity<Map<String, Object>> readiness(Principal principal) {
        return ResponseEntity.ok(coachService.readiness(currentUser.requireId(principal)));
    }

    public record StartRequest(UUID jobId, UUID applicationId, String targetRole, String targetCompany,
                               String jobDescription, List<String> questionTypes, String difficulty,
                               Integer questionCount) {
    }

    public record AnswerRequest(UUID questionId, String answer, Integer timeTakenSeconds) {
    }
}
