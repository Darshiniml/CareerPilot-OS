package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotPlan;
import org.springframework.stereotype.Service;

@Service
public class PlanningEngine {
    public CopilotPlan createPlan(CopilotContext context) {
        CopilotPlan plan = new CopilotPlan();
        switch (context.getIntent()) {
            case INTERVIEW_PREPARATION -> {
                plan.setPlanType("Interview Preparation Plan");
                plan.getTasks().add("Review technical concepts");
                plan.getTasks().add("Practice coding and behavioral stories");
                plan.getTasks().add("Review company-specific context");
            }
            case GAP_ANALYSIS -> {
                plan.setPlanType("Learning Plan");
                plan.getTasks().add("Identify missing skills");
                plan.getTasks().add("Prioritize targeted study topics");
                plan.getTasks().add("Track progress weekly");
            }
            case JOB_SEARCH -> {
                plan.setPlanType("Job Search Plan");
                plan.getTasks().add("Rank matching roles");
                plan.getTasks().add("Prepare tailored applications");
                plan.getTasks().add("Monitor company fit");
            }
            default -> {
                plan.setPlanType("Career Growth Plan");
                plan.getTasks().add("Review current progress");
                plan.getTasks().add("Take next best action");
            }
        }
        return plan;
    }
}
