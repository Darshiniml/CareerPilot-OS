package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.matching.SkillGapService;
import com.careerpilot.backend.modules.analytics.domain.LearningPath;
import com.careerpilot.backend.modules.analytics.domain.LearningPathItem;
import com.careerpilot.backend.modules.analytics.repositories.LearningPathRepository;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Learning recommendations from real skill gaps (job demand across analysed matching jobs + weak
 * interview areas) and AI learning plans for those gaps. Resource suggestions never carry links.
 */
@Service
@RequiredArgsConstructor
public class LearningPlanService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillGapService skillGapService;
    private final InterviewCoachService interviewCoachService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final LearningPathRepository learningPathRepository;
    private final AiGatewayClient gatewayClient;

    /** Prioritised gaps with the evidence behind each one. Empty with a reason when there is no data. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> recommendations(UUID userId) {
        List<Map<String, Object>> gaps = new ArrayList<>();
        SkillGapService.SkillLandscape landscape = skillGapService.landscape(userId);
        if (landscape.available()) {
            for (SkillGapService.SkillCount c : landscape.missingSkills()) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("skill", c.skill());
                g.put("source", "JOB_DEMAND");
                g.put("evidence", "Required by " + c.jobs() + " of " + landscape.analysedJobs() + " analysed jobs you were matched against");
                g.put("priority", c.jobs() * 100 / Math.max(1, landscape.analysedJobs()) >= 40 ? "HIGH" : "MEDIUM");
                g.put("demandJobs", c.jobs());
                gaps.add(g);
            }
        }
        Map<String, Object> readiness = interviewCoachService.readiness(userId);
        if (Boolean.TRUE.equals(readiness.get("available"))) {
            for (Map<String, Object> area : (List<Map<String, Object>>) readiness.getOrDefault("weakestAreas", List.of())) {
                Object score = area.get("score");
                if (score instanceof Number n && n.doubleValue() < 0.6) {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("skill", area.get("skillArea"));
                    g.put("source", "INTERVIEW_PRACTICE");
                    g.put("evidence", "Average practice-interview score " + Math.round(n.doubleValue() * 100) + "%");
                    g.put("priority", n.doubleValue() < 0.4 ? "HIGH" : "MEDIUM");
                    gaps.add(g);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gaps", gaps);
        if (gaps.isEmpty()) {
            result.put("reason", landscape.available() ? "No skill gaps found in your analysed matches"
                    : landscape.reason());
        }
        return result;
    }

    /** Generate and persist an AI learning plan for one skill. Not transactional: no DB connection is
     *  held during the model call; the plan is persisted with a single save. */
    @SuppressWarnings("unchecked")
    public LearningPath createPlan(UUID userId, String skill, String currentLevel, String targetLevel, Integer weeklyHours) {
        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException("skill is required");
        }
        String normalized = skill.trim();
        String evidence = ((List<Map<String, Object>>) recommendations(userId).get("gaps")).stream()
                .filter(g -> normalized.equalsIgnoreCase(String.valueOf(g.get("skill"))))
                .map(g -> String.valueOf(g.get("evidence"))).findFirst()
                .orElse("Chosen by you");
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("skill", normalized);
        gap.put("priority", 1);
        gap.put("evidence", evidence);
        gap.put("currentLevel", currentLevel == null ? "unknown" : currentLevel);
        gap.put("targetLevel", targetLevel == null ? "job-ready" : targetLevel);

        Map<String, Object> payload = new HashMap<>();
        payload.put("skillGaps", List.of(gap));
        payload.put("candidate", Map.of("skills", candidateKnowledgeService.skillNames(userId)));
        if (weeklyHours != null) payload.put("weeklyHours", weeklyHours);
        Map<String, Object> result = gatewayClient.run("LEARNING_RECOMMENDATIONS", payload);
        List<Map<String, Object>> plans = (List<Map<String, Object>>) result.getOrDefault("plans", List.of());
        if (plans.isEmpty()) {
            throw new IllegalStateException("The AI returned no plan; please try again");
        }
        Map<String, Object> plan = plans.get(0);

        LearningPath path = learningPathRepository.findByCandidateIdAndSkill(userId, normalized)
                .orElseGet(() -> LearningPath.builder().id(UUID.randomUUID()).candidateId(userId).skill(normalized)
                        .createdAt(Instant.now()).build());
        path.setCurrentLevel(currentLevel == null ? "UNKNOWN" : currentLevel);
        path.setTargetLevel(targetLevel == null ? "JOB_READY" : targetLevel);
        path.setPriority(evidence.startsWith("Required by") ? "HIGH" : "MEDIUM");
        path.setExpectedMatchImprovement(0.0); // not predicted: we do not invent outcome numbers
        path.setWhyItMatters(Objects.toString(plan.get("whyItMatters"), null));
        path.setPracticeProject(Objects.toString(plan.get("practiceProject"), null));
        path.setDemandEvidence(evidence);
        if (path.getLearningSequence() == null) {
            path.setLearningSequence(new ArrayList<>());
        }
        path.getLearningSequence().clear();
        int seq = 1;
        double totalHours = 0;
        String resources = json(plan.getOrDefault("resources", List.of()));
        for (Map<String, Object> step : (List<Map<String, Object>>) plan.getOrDefault("steps", List.of())) {
            Double hours = step.get("estimatedHours") instanceof Number n ? n.doubleValue() : null;
            if (hours != null) totalHours += hours;
            path.getLearningSequence().add(LearningPathItem.builder()
                    .id(UUID.randomUUID())
                    .learningPath(path)
                    .sequenceNumber(seq++)
                    .stepName(Objects.toString(step.get("title"), "Step"))
                    .description(Objects.toString(step.get("description"), null))
                    .estimatedHours(hours)
                    .resourcesJson(seq == 2 ? resources : null)
                    .build());
        }
        path.setEstimatedHours((int) Math.round(totalHours));
        return learningPathRepository.save(path);
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
