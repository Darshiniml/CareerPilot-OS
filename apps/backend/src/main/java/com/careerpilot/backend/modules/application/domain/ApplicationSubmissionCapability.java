package com.careerpilot.backend.modules.application.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmissionCapability {
    private String source;
    private ApplicationSubmissionMode submissionMode;
    private boolean enabled;
    private boolean requiresCredentials;
    private boolean supportsSubmission;
    private boolean supportsVerification;
    private boolean supportsStatusTracking;
    private String reason;
    private String configurationStatus;
    private String healthStatus;
}
