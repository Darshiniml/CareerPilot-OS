package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsTest {

    @Test
    void exposesCoreApplicationMetrics() {
        ApplicationRecordRepository repository = Mockito.mock(ApplicationRecordRepository.class);
        Mockito.when(repository.countByWorkflowState(WorkflowState.SUBMITTED)).thenReturn(4L);
        ApplicationOrchestratorService service = new ApplicationOrchestratorService(
                repository,
                null,
                null,
                null,
                null,
                null,
                new ApplicationWorkflowEngine(),
                new EligibilityEngine(),
                new ResumeSelectionEngine(),
                new ApprovalPolicyEngine(),
                new DefaultSubmissionAdapter(),
                null
        );

        Map<String, Object> result = service.statistics();

        assertThat(result).containsKey("applicationsSubmitted");
        assertThat(result.get("applicationsSubmitted")).isEqualTo(4L);
    }
}
