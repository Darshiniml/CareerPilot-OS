package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {
    @Test
    void buildsRelevantContextForInterviewRequests() {
        ContextBuilder builder = new ContextBuilder();
        CopilotContext context = builder.build(CopilotIntent.INTERVIEW_PREPARATION, null, null, null);

        assertThat(context.getInterviewData()).containsKey("stage");
        assertThat(context.getIntent()).isEqualTo(CopilotIntent.INTERVIEW_PREPARATION);
    }
}
