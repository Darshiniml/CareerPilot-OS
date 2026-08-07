package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmissionResult {
    private boolean success;
    private String applicationExternalId;
    private String status; // e.g., SUBMITTED, FAILED
    private String trackingUrl;
    private String errorMessage;
}
