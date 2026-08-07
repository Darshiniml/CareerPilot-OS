package com.careerpilot.shared.dto.ai.matching;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMatchRequestDto {
    @NotNull
    private UUID candidateId;

    @NotEmpty
    private List<UUID> jobIds;

    private UUID companyId;
    private UUID userId;
    private MatchWeightsDto customWeights;
    private boolean skipCache;
}
