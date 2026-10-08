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
public class MatchResultDto {
    /** Factors excluded from the score because the needed data was missing. */
    private List<String> notAssessedFactors;
    /** False when the job has not been AI-analysed yet (requirement-based factors are then not assessed). */
    private Boolean jobAnalyzed;
    private UUID matchId;
    private UUID candidateId;
    private UUID jobId;
    private UUID companyId;
    private UUID userId;
    private double overallScore;
    private Map<String, Double> individualScores;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<GapItemDto> criticalGaps;
    private List<GapItemDto> recommendedImprovements;
    private List<GapItemDto> optionalImprovements;
    private List<RecommendationItemDto> recommendations;
    private double confidenceScore;
    private String explanation;
    private List<String> matchedSkills;
    private List<String> missingSkills;
}
