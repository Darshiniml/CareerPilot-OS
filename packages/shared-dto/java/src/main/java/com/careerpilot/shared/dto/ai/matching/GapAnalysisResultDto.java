package com.careerpilot.shared.dto.ai.matching;

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
public class GapAnalysisResultDto {
    private UUID candidateId;
    private UUID jobId;
    private UUID companyId;
    private List<GapItemDto> criticalGaps;
    private List<GapItemDto> recommendedImprovements;
    private List<GapItemDto> optionalImprovements;
}
