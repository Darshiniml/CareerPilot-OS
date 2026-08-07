package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrieverTest {
    @Test
    void retrievesRequiredKnowledgeByModule() {
        KnowledgeRetriever retriever = new KnowledgeRetriever();
        CopilotContext context = new CopilotContext();
        context.setIntent(CopilotIntent.MATCH_EXPLANATION);
        context.setResumeKnowledge(java.util.Map.of("summary", "Java backend"));
        context.setJobKnowledge(java.util.Map.of("title", "Backend Engineer"));

        var retrieved = retriever.retrieve(context, CopilotIntent.MATCH_EXPLANATION);
        assertThat(retrieved).containsKey("resume");
        assertThat(retrieved).containsKey("job");
    }
}
