package com.careerpilot.shared.dto.ai.matching;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class RankingRequestDto {
    @NotNull
    private UUID candidateId;

    @NotEmpty
    private List<MatchResultDto> matchResults;

    private UUID companyId;
    private UUID userId;
    private String rankingStrategy;
    private MatchWeightsDto customWeights;
    private Double minScore;
    private Integer limit;
}
