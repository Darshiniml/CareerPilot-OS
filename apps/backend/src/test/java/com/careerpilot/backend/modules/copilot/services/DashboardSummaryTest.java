package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardSummaryTest {
    @Test
    void buildsDashboardSummary() {
        DashboardSummaryService service = new DashboardSummaryService();
        CopilotContext context = new CopilotContext();
        context.setApplicationData(java.util.Map.of("submittedThisWeek", 3));
        context.setMatchingData(java.util.Map.of("averageScore", 0.81));

        var summary = service.buildSummary(context);
        assertThat(summary).containsKey("applicationsSubmitted");
    }
}
