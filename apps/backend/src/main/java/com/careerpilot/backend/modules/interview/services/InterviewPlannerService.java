package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewKnowledge;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class InterviewPlannerService {

    public InterviewKnowledge buildKnowledge(UUID applicationId, UUID candidateId, UUID companyId, UUID jobId, String stage, String interviewType) {
        InterviewKnowledge knowledge = InterviewKnowledge.builder()
                .interviewId(UUID.randomUUID())
                .applicationId(applicationId)
                .candidateId(candidateId)
                .companyId(companyId)
                .jobId(jobId)
                .interviewStage(com.careerpilot.backend.modules.interview.domain.InterviewStage.valueOf(stage.toUpperCase(Locale.ROOT)))
                .interviewType(com.careerpilot.backend.modules.interview.domain.InterviewType.valueOf(interviewType.toUpperCase(Locale.ROOT)))
                .estimatedDifficulty("medium")
                .preparationPriority("high")
                .requiredTechnologies(List.of("java", "spring"))
                .requiredConcepts(List.of("collections", "rest apis"))
                .behavioralTopics(List.of("teamwork", "leadership"))
                .codingTopics(List.of("arrays", "strings"))
                .systemDesignTopics(List.of("microservices"))
                .preparationChecklist(List.of("Review core concepts", "Practice coding", "Prepare behavioral stories"))
                .createdAt(Instant.now())
                .build();
        return knowledge;
    }

    public Map<String, Object> buildStudyPlan(InterviewKnowledge knowledge) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planType", "3 Day Plan");
        plan.put("focusAreas", knowledge.getRequiredTechnologies());
        plan.put("dailyPlan", List.of(
                Map.of("day", 1, "goal", "Review Java and Spring concepts"),
                Map.of("day", 2, "goal", "Practice coding and system design"),
                Map.of("day", 3, "goal", "Prepare behavioral stories and company context")
        ));
        return plan;
    }
}
