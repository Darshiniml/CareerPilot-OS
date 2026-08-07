package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.VerificationStatus;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class VerificationEngine {

    public VerificationResult verify(ApplicationRecord application) {
        if (application == null) {
            return new VerificationResult(VerificationStatus.FAILED, "Application missing");
        }
        if (application.getWorkflowState() == WorkflowState.SUBMITTED) {
            application.setLastVerifiedAt(Instant.now());
            return new VerificationResult(VerificationStatus.VERIFIED, "Application verified");
        }
        if (application.getWorkflowState() == WorkflowState.SUBMITTING || application.getWorkflowState() == WorkflowState.RETRYING) {
            return new VerificationResult(VerificationStatus.PENDING, "Verification in progress");
        }
        return new VerificationResult(VerificationStatus.UNKNOWN, "Verification unavailable");
    }

    public record VerificationResult(VerificationStatus status, String message) {
    }
}
