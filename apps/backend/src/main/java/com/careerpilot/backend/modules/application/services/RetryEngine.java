package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.RetryAttempt;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.RetryAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryEngine {

    private final RetryAttemptRepository retryAttemptRepository;

    public RetryDecision decide(ApplicationRecord application, String reason) {
        int nextAttempt = application.getRetryCount() + 1;
        boolean shouldRetry = nextAttempt <= 3 && !"permanent".equalsIgnoreCase(reason);
        return new RetryDecision(shouldRetry, nextAttempt, Instant.now().plusSeconds(30L * nextAttempt));
    }

    public void record(ApplicationRecord application, RetryDecision decision, String reason) {
        retryAttemptRepository.save(RetryAttempt.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getApplicationId())
                .attemptNumber(decision.attemptNumber())
                .reason(reason)
                .permanentFailure(!decision.shouldRetry())
                .nextAttemptAt(decision.nextAttemptAt())
                .createdAt(Instant.now())
                .build());
    }

    public List<RetryAttempt> history(UUID applicationId) {
        return retryAttemptRepository.findByApplicationIdOrderByAttemptNumberAsc(applicationId);
    }

    public record RetryDecision(boolean shouldRetry, int attemptNumber, Instant nextAttemptAt) {
    }
}
