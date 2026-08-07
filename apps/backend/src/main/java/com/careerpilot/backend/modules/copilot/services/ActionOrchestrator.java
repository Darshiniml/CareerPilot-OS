package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.springframework.stereotype.Service;

@Service
public class ActionOrchestrator {
    public String execute(CopilotContext context) {
        CopilotIntent intent = context.getIntent();
        return switch (intent) {
            case JOB_SEARCH -> "Search Jobs";
            case MATCH_EXPLANATION -> "Explain Match";
            case INTERVIEW_PREPARATION -> "Prepare Interview";
            case GAP_ANALYSIS -> "Generate Study Plan";
            case APPLICATION_STATUS -> "Track Applications";
            case COMPANY_SEARCH -> "Recommend Companies";
            case DASHBOARD_SUMMARY -> "Summarize Progress";
            case LEARNING_RECOMMENDATION -> "Show Readiness";
            default -> "Provide Guidance";
        };
    }
}
