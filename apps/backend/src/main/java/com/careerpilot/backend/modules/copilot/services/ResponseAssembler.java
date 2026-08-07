package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotPlan;
import com.careerpilot.backend.modules.copilot.domain.CopilotRecommendation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ResponseAssembler {
    public Map<String, Object> assemble(CopilotContext context, CopilotPlan plan, List<CopilotRecommendation> recommendations) {
        return Map.of(
                "intent", context.getIntent(),
                "plan", plan,
                "recommendations", recommendations,
                "retrievedContext", context.getRetrievedKnowledge()
        );
    }
}
