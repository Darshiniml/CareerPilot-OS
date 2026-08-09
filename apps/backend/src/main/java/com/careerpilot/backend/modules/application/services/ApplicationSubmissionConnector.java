package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

public interface ApplicationSubmissionConnector {

    String getConnectorId();

    ApplicationSubmissionMode getSubmissionMode();

    boolean isSubmissionSupported();

    SubmissionResult executeSubmission(ApplicationRecord record, Map<String, Object> applicationPackage);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class SubmissionResult {
        private boolean success;
        private String statusMessage;
        private String externalApplicationId;
        private String confirmationUrl;
        private String failureReason;
        private ApplicationSubmissionMode mode;
        private Instant timestamp;
        private Map<String, Object> evidencePayload;
    }
}
