package com.careerpilot.shared.dto.ai.resume;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsQualityMetricsDto {
    private double atsScore;
    private double completenessScore;
    private double readabilityScore;
    private double sectionCoverage;
    private double contactQuality;
    private double formattingQuality;
    private double keywordCoverage;
    private double skillDiversity;
    private double projectStrength;
    private double experienceStrength;
    private Map<String, Object> details;
}
