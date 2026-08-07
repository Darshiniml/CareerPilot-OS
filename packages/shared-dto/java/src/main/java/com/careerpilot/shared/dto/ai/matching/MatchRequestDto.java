package com.careerpilot.shared.dto.ai.matching;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRequestDto {
    @NotNull
    private UUID candidateId;

    @NotNull
    private UUID jobId;

    private UUID companyId;
    private UUID userId;
    private MatchWeightsDto customWeights;
    private boolean skipCache;
}
