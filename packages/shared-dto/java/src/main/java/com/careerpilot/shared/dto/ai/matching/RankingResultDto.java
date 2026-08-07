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
public class RankingResultDto {
    private UUID candidateId;
    private String rankingStrategy;
    private List<RankedJobDto> rankedJobs;
    private int totalJobs;
    private long latencyMs;
}
