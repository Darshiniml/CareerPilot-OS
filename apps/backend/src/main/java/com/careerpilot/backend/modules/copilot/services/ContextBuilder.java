package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContextBuilder {
    public CopilotContext build(CopilotIntent intent, Map<String, Object> resume, Map<String, Object> job, Map<String, Object> preferences) {
        CopilotContext context = new CopilotContext();
        context.setIntent(intent);
        context.setResumeKnowledge(resume == null ? Map.of("summary", "Backend developer") : resume);
        context.setJobKnowledge(job == null ? Map.of("title", "Software Engineer") : job);
        context.setPreferences(preferences == null ? Map.of("remote", true) : preferences);

        Map<String, Object> retrieved = new LinkedHashMap<>();
        retrieved.put("resume", context.getResumeKnowledge());
        retrieved.put("job", context.getJobKnowledge());
        retrieved.put("preferences", context.getPreferences());

        if (intent == CopilotIntent.INTERVIEW_PREPARATION) {
            context.setInterviewData(Map.of("stage", "technical", "readiness", 0.74));
            retrieved.put("interview", context.getInterviewData());
        }
        if (intent == CopilotIntent.MATCH_EXPLANATION || intent == CopilotIntent.GAP_ANALYSIS) {
            context.setMatchingData(Map.of("overallScore", 0.74, "missingSkills", java.util.List.of("aws")));
            retrieved.put("matching", context.getMatchingData());
        }
        context.setRetrievedKnowledge(retrieved);
        return context;
    }
}
