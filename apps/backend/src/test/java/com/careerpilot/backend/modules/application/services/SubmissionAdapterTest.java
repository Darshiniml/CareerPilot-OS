package com.careerpilot.backend.modules.application.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionAdapterTest {

    @Test
    void requiresManualActionWithoutAPermittedSubmissionProvider() {
        DefaultSubmissionAdapter adapter = new DefaultSubmissionAdapter();

        SubmissionAdapter.SubmissionResult result = adapter.submit(Map.of(
                "candidateId", "candidate-1",
                "companyId", "company-1",
                "jobId", "job-1"
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExternalReference()).isNull();
        assertThat(result.getStatus()).isEqualTo("manual_action_required");
    }
}
