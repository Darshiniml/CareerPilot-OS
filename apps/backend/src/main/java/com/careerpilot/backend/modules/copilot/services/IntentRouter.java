package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.springframework.stereotype.Service;

@Service
public class IntentRouter {
    public CopilotIntent classify(String request) {
        if (request == null || request.isBlank()) {
            return CopilotIntent.GENERAL_HELP;
        }
        String normalized = request.toLowerCase();
        if (normalized.contains("job") || normalized.contains("jobs")) {
            return CopilotIntent.JOB_SEARCH;
        }
        if (normalized.contains("company") || normalized.contains("companies")) {
            return CopilotIntent.COMPANY_SEARCH;
        }
        if (normalized.contains("resume") || normalized.contains("cv")) {
            return CopilotIntent.RESUME_REVIEW;
        }
        if (normalized.contains("match") || normalized.contains("explain")) {
            return CopilotIntent.MATCH_EXPLANATION;
        }
        if (normalized.contains("gap") || normalized.contains("improve")) {
            return CopilotIntent.GAP_ANALYSIS;
        }
        if (normalized.contains("interview") || normalized.contains("prepare")) {
            return CopilotIntent.INTERVIEW_PREPARATION;
        }
        if (normalized.contains("application") || normalized.contains("status")) {
            return CopilotIntent.APPLICATION_STATUS;
        }
        if (normalized.contains("learn") || normalized.contains("study")) {
            return CopilotIntent.LEARNING_RECOMMENDATION;
        }
        if (normalized.contains("career") || normalized.contains("growth")) {
            return CopilotIntent.CAREER_PLANNING;
        }
        if (normalized.contains("dashboard") || normalized.contains("summary")) {
            return CopilotIntent.DASHBOARD_SUMMARY;
        }
        return CopilotIntent.GENERAL_HELP;
    }
}
