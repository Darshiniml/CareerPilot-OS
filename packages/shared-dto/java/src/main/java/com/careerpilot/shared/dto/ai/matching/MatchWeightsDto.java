package com.careerpilot.shared.dto.ai.matching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchWeightsDto {
    private Double skillMatch;
    private Double experienceMatch;
    private Double projectMatch;
    private Double technologyMatch;
    private Double locationMatch;
    private Double salaryMatch;
    private Double cultureMatch;
    private Double educationMatch;
    private Double certificationMatch;
    private Double responsibilityMatch;
    private Double industryMatch;
    private Double remotePreferenceMatch;
    private Double employmentTypeMatch;
    private Double careerGrowthMatch;
    private Double learningOpportunityMatch;
}
