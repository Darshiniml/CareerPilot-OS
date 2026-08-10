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
    private final LearningPathEngine learningPathEngine;
    private final LearningPathRepository learningPathRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CareerGoalRepository careerGoalRepository;
    private final CareerGoalProgressRepository goalProgressRepository;
    private final DataCollector dataCollector;
    private final SkillDemandAnalyzer skillDemandAnalyzer;
    private final InterviewAnalyzer interviewAnalyzer;
    private final CareerInsights careerInsights;
    private final UserRepository userRepository;
    private final CareerLearningRecommendationService careerLearningRecommendationService;

    @PostMapping("/path")
    @Operation(summary = "Generate a prerequisite-aware learning path for a skill")
    public ResponseEntity<LearningPath> createPath(Principal principal, @RequestBody PathRequest request) {
        UUID candidateId = getUserId(principal);
        LearningPath path = learningPathEngine.generateLearningPath(candidateId, request.skill(), request.currentLevel(), request.targetLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(path);
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
    public ResponseEntity<List<Map<String, Object>>> getRecommendations(Principal principal) {
        UUID candidateId = getUserId(principal);
        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        
        Set<String> candidateSkills = new HashSet<>();
        var resumeCache = (com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache) collected.get("resumeCache");
        if (resumeCache != null) {
            Map<String, Object> knowledge = resumeCache.getStructuredKnowledge();
            if (knowledge != null && knowledge.containsKey("skills")) {
                List<?> rawSkills = (List<?>) knowledge.get("skills");
                for (Object item : rawSkills) {
                    if (item instanceof Map<?, ?> map) {
                        Object skillVal = map.get("skill");
                        if (skillVal == null) skillVal = map.get("name");
                        if (skillVal != null) candidateSkills.add(skillVal.toString().toLowerCase().trim());
                    } else if (item != null) {
                        candidateSkills.add(item.toString().toLowerCase().trim());
                    }
                }
            }
        }

        Map<String, Object> skillAnalysis = skillDemandAnalyzer.analyzeSkills(candidateSkills);
        List<String> missingSkills = (List<String>) skillAnalysis.getOrDefault("missingSkills", List.of());
        Map<String, Double> demandPercentages = (Map<String, Double>) skillAnalysis.getOrDefault("demandPercentages", Map.of());

        var ints = (List<com.careerpilot.backend.modules.interview.domain.InterviewSession>) collected.getOrDefault("interviews", List.of());
        Map<String, Object> intAnalysis = interviewAnalyzer.analyzeInterviews(ints);
        Map<String, Object> scores = (Map<String, Object>) intAnalysis.getOrDefault("averageQuestionScores", Map.of());

        List<Map<String, Object>> recommendations = careerInsights.generateRecommendations(missingSkills, demandPercentages, scores);
        return ResponseEntity.ok(recommendations);
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

    public record PathRequest(String skill, String currentLevel, String targetLevel) {}
    public record ProgressRequest(String skill, double progressPercentage, String status) {}
    public record GoalRequest(String targetRole, String targetIndustry, Double targetSalary, String targetLocation, Integer timelineMonths) {}
    public record GoalProgressUpdateRequest(double learningPlanProgress, double overallProgress, boolean isCompleted) {}
}
