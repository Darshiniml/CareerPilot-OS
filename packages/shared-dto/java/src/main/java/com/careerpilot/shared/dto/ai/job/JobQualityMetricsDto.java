package com.careerpilot.shared.dto.ai.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobQualityMetricsDto {
    private Double skillDensity;
    private Double technologyDiversity;
    private Double seniorityComplexity;
    private Double requirementCompleteness;
    private Double jobDetailQuality;
    private Double remoteFriendliness;
    private Double learningOpportunity;
}
