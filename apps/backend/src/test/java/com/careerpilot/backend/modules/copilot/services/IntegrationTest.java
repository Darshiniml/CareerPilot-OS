package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTest {
    @Test
    void orchestratesPlanRecommendationAndSummary() {
        IntentRouter router = new IntentRouter();
        ContextBuilder builder = new ContextBuilder();
        PlanningEngine planningEngine = new PlanningEngine();
        RecommendationEngine recommendationEngine = new RecommendationEngine();
        DashboardSummaryService dashboardService = new DashboardSummaryService();

        CopilotIntent intent = router.classify("Prepare me for a technical interview");
        CopilotContext context = builder.build(intent, null, null, null);
        var plan = planningEngine.createPlan(context);
        var recommendations = recommendationEngine.generate(context);
        var summary = dashboardService.buildSummary(context);

        assertThat(intent).isEqualTo(CopilotIntent.INTERVIEW_PREPARATION);
        assertThat(plan.getTasks()).isNotEmpty();
        assertThat(recommendations).isNotEmpty();
        assertThat(summary).containsKey("applicationsSubmitted");
    }
}
