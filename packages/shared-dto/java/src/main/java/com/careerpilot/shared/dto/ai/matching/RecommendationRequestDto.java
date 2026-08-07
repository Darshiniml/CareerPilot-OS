package com.careerpilot.shared.dto.ai.matching;

import jakarta.validation.constraints.NotNull;
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
public class RecommendationRequestDto {
    @NotNull
    private UUID candidateId;

    @NotNull
    private UUID jobId;

    private UUID companyId;
    private UUID userId;

    private Map<String, Object> candidateKnowledge;
    private Map<String, Object> candidateQualityMetrics;
    private Map<String, Object> candidatePreferences;
    private Map<String, Object> companyKnowledge;
    private Map<String, Object> companyMetadata;
    private Map<String, Object> companyInsights;
    private Map<String, Object> jobKnowledge;
    private Map<String, Object> jobMetadata;
    private Map<String, Object> jobInsights;
}
