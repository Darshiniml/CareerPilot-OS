package com.careerpilot.shared.dto.ai.matching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedJobDto {
    private int rank;
    private UUID jobId;
    private UUID companyId;
    private double overallScore;
    private double skillScore;
    private double companyFitScore;
    private double careerGrowthScore;
    private double salaryScore;
    private double learningOpportunityScore;
    private Map<String, Double> individualScores;
    private List<String> strengths;
    private List<String> weaknesses;
}
