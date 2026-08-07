package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KnowledgeRetriever {
    public Map<String, Object> retrieve(CopilotContext context, CopilotIntent intent) {
        Map<String, Object> knowledge = new LinkedHashMap<>();
        if (context.getResumeKnowledge() != null) {
            knowledge.put("resume", context.getResumeKnowledge());
        }
        if (context.getJobKnowledge() != null) {
            knowledge.put("job", context.getJobKnowledge());
        }
        if (context.getMatchingData() != null) {
            knowledge.put("matching", context.getMatchingData());
        }
        if (context.getInterviewData() != null) {
            knowledge.put("interview", context.getInterviewData());
        }
        if (context.getApplicationData() != null) {
            knowledge.put("application", context.getApplicationData());
        }
        if (context.getLearningData() != null) {
            knowledge.put("learning", context.getLearningData());
        }
        context.setRetrievedKnowledge(knowledge);
        return knowledge;
    }
}
