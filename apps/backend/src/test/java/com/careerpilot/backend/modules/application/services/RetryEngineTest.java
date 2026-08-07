package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.RetryAttemptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetryEngineTest {

    @Test
    void retriesUpToThreeTimesBeforeFailingPermanently() {
        RetryAttemptRepository repository = Mockito.mock(RetryAttemptRepository.class);
        RetryEngine engine = new RetryEngine(repository);
        ApplicationRecord application = ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .workflowState(WorkflowState.FAILED)
                .retryCount(2)
                .build();

        RetryEngine.RetryDecision decision = engine.decide(application, "timeout");

        assertThat(decision.shouldRetry()).isTrue();
        assertThat(decision.attemptNumber()).isEqualTo(3);
    }
}
