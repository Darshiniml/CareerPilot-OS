package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmissionQuery {
    private UUID userId;
    private String jobId;
    private Map<String, String> formAnswers;
    private String resumeUrl;
    private String coverLetterText;
}
