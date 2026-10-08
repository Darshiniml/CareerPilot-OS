package com.careerpilot.backend.modules.analytics.controllers;

import com.careerpilot.backend.modules.analytics.domain.*;
import com.careerpilot.backend.modules.analytics.repositories.*;
import com.careerpilot.backend.modules.analytics.services.*;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/learning")
@Tag(name = "Learning Engine", description = "Endpoints for prerequisite-aware learning paths, goal setup, and progress tracking")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LearningController {

    private final CareerAnalyticsService analyticsService;
    private final com.careerpilot.backend.modules.analytics.services.LearningPlanService learningPlanService;
    private final LearningPathRepository learningPathRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CareerGoalRepository careerGoalRepository;
    private final CareerGoalProgressRepository goalProgressRepository;
    private final DataCollector dataCollector;
    private final UserRepository userRepository;
    private final CareerLearningRecommendationService careerLearningRecommendationService;

    @PostMapping("/path")
    @Operation(summary = "Generate an AI learning plan for a skill (grounded in your real skill gaps)")
    public ResponseEntity<LearningPath> createPath(Principal principal, @RequestBody PathRequest request) {
        UUID candidateId = getUserId(principal);
        LearningPath path = learningPlanService.createPlan(candidateId, request.skill(), request.currentLevel(),
                request.targetLevel(), request.weeklyHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(path);
    }

    @GetMapping("/paths")
    public ResponseEntity<List<LearningPath>> listPaths(Principal principal) {
        return ResponseEntity.ok(learningPathRepository.findByCandidateId(getUserId(principal)));
    }

    @GetMapping("/path/{id}")
    public ResponseEntity<LearningPath> getPath(Principal principal, @PathVariable UUID id) {
        Optional<LearningPath> pathOpt = learningPathRepository.findById(id);
        if (pathOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        LearningPath path = pathOpt.get();
        UUID candidateId = getUserId(principal);
        if (!path.getCandidateId().equals(candidateId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(path);
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Skill gaps to learn, with the real evidence for each (job demand, interview practice)")
    public ResponseEntity<Map<String, Object>> getRecommendations(Principal principal) {
        return ResponseEntity.ok(learningPlanService.recommendations(getUserId(principal)));
    }

    @GetMapping("/career-recommendations")
    @Operation(summary = "Get career learning recommendations (Milestone 20)")
    public ResponseEntity<List<CareerLearningRecommendationService.CareerLearningRecommendation>> getCareerRecommendations(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(careerLearningRecommendationService.getCareerRecommendations(candidateId));
    }

    @PostMapping("/progress")
    public ResponseEntity<LearningProgress> updateProgress(Principal principal, @RequestBody ProgressRequest request) {
        UUID candidateId = getUserId(principal);
        LearningProgress progress = analyticsService.updateLearningProgress(candidateId, request.skill(), request.progressPercentage(), request.status());
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/progress")
    public ResponseEntity<List<LearningProgress>> getProgress(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(learningProgressRepository.findByCandidateId(candidateId));
    }

    @PostMapping("/goals")
    public ResponseEntity<CareerGoal> createGoal(Principal principal, @RequestBody GoalRequest request) {
        UUID candidateId = getUserId(principal);
        CareerGoal goal = analyticsService.createGoal(candidateId, request.targetRole(), request.targetIndustry(), request.targetSalary(), request.targetLocation(), request.timelineMonths());
        return ResponseEntity.status(HttpStatus.CREATED).body(goal);
    }

    @GetMapping("/goals")
    public ResponseEntity<List<Map<String, Object>>> getGoals(Principal principal) {
        UUID candidateId = getUserId(principal);
        List<CareerGoal> goals = careerGoalRepository.findByCandidateId(candidateId);
        List<Map<String, Object>> responseList = new ArrayList<>();

        for (CareerGoal goal : goals) {
            Map<String, Object> goalMap = new HashMap<>();
            goalMap.put("goal", goal);
            
            Optional<CareerGoalProgress> progressOpt = goalProgressRepository.findByGoalId(goal.getId());
            goalMap.put("progress", progressOpt.orElse(null));
            
            responseList.add(goalMap);
        }

        return ResponseEntity.ok(responseList);
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<CareerGoalProgress> updateGoal(Principal principal, @PathVariable UUID id, @RequestBody GoalProgressUpdateRequest request) {
        Optional<CareerGoal> goalOpt = careerGoalRepository.findById(id);
        if (goalOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        UUID candidateId = getUserId(principal);
        if (!goalOpt.get().getCandidateId().equals(candidateId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        CareerGoalProgress progress = analyticsService.updateGoalProgress(id, request.learningPlanProgress(), request.overallProgress(), request.isCompleted());
        return ResponseEntity.ok(progress);
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Unauthorized");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }

    public record PathRequest(String skill, String currentLevel, String targetLevel, Integer weeklyHours) {}
    public record ProgressRequest(String skill, double progressPercentage, String status) {}
    public record GoalRequest(String targetRole, String targetIndustry, Double targetSalary, String targetLocation, Integer timelineMonths) {}
    public record GoalProgressUpdateRequest(double learningPlanProgress, double overallProgress, boolean isCompleted) {}
}
