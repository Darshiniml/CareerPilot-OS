package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationTrackingResult {
    private String applicationExternalId;
    private String status; // e.g., SUBMITTED, UNDER_REVIEW, INTERVIEWING, OFFER, REJECTED
    private Instant lastUpdatedAt;
    private List<String> historyLogs;
}
