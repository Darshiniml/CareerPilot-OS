package com.careerpilot.shared.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GapAnalysisCompletedEvent extends BaseEvent {
    private UUID candidateId;
    private UUID jobId;
    private UUID companyId;
    private UUID userId;
    private int criticalGapCount;
    private int recommendedImprovementCount;
}
