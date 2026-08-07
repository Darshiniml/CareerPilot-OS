package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPreparationResult {
    private String jobId;
    private List<ApplicationFormQuestion> questions;
    private Map<String, String> defaultAnswers;
    private boolean requiresHumanReview;
}
