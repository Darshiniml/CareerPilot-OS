package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningEngineTest {
    @Test
    void createsStructuredTasksForInterviewPlans() {
        PlanningEngine engine = new PlanningEngine();
        CopilotContext context = new CopilotContext();
        context.setIntent(CopilotIntent.INTERVIEW_PREPARATION);

        var plan = engine.createPlan(context);
        assertThat(plan.getTasks()).isNotEmpty();
        assertThat(plan.getPlanType()).isEqualTo("Interview Preparation Plan");
    }
}
