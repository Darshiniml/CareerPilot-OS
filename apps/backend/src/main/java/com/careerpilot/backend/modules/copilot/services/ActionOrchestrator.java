package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.agent.domain.AgentPolicy;
import com.careerpilot.backend.modules.agent.domain.AgentWorkflow;
import com.careerpilot.backend.modules.agent.services.AgentExecutionService;
import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ActionOrchestrator {

    private final AgentExecutionService agentExecutionService;

    public ActionOrchestrator(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    public String execute(CopilotContext context) {
        String query = (String) context.getRetrievedKnowledge().getOrDefault("query", "");
        String userIdStr = context.getUserId();
        
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (Exception e) {
            userId = UUID.randomUUID(); // Fallback for demo/unauthenticated sessions
        }

        String normalized = query.toLowerCase();
        
        // 1. Interpret auto-apply intent
        if (normalized.contains("apply automatically") || normalized.contains("auto apply") || normalized.contains("auto-apply")) {
            AgentPolicy policy = agentExecutionService.getOrCreatePolicy(userId);
            
            if (!policy.getAllowAutomaticSubmission()) {
                String reply = "To apply automatically, you must first enable 'Automatic Submission' in your automation settings policy dashboard.";
                context.getRetrievedKnowledge().put("copilotResponse", reply);
                return reply;
            }
            
            // Extract potential match score threshold
            double matchScore = 85.0; // Default if not specified
            if (normalized.contains("85")) matchScore = 85.0;
            else if (normalized.contains("90")) matchScore = 90.0;
            else if (normalized.contains("80")) matchScore = 80.0;
            else if (normalized.contains("75")) matchScore = 75.0;
            
            policy.setMinimumMatchScore(matchScore);
            agentExecutionService.updatePolicy(userId, policy);
            
            AgentWorkflow workflow = agentExecutionService.startWorkflow(userId);
            
            String reply = String.format("I have set your minimum match score to %.0f%% and started the autonomous application submission workflow. Tracking ID: %s", 
                    matchScore, workflow.getId());
            context.getRetrievedKnowledge().put("copilotResponse", reply);
            context.getRetrievedKnowledge().put("workflowId", workflow.getId().toString());
            return reply;
        }

        // 2. Interpret jobs discovery intent
        if (normalized.contains("find") && (normalized.contains("job") || normalized.contains("jobs"))) {
            AgentWorkflow workflow = agentExecutionService.startWorkflow(userId);
            String reply = String.format("I have started an autonomous career search workflow for you. Tracking ID: %s. You can view its progress on the /agents dashboard.", 
                    workflow.getId());
            context.getRetrievedKnowledge().put("copilotResponse", reply);
            context.getRetrievedKnowledge().put("workflowId", workflow.getId().toString());
            return reply;
        }

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
