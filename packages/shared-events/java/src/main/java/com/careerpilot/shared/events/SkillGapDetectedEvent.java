package com.careerpilot.shared.events;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGapDetectedEvent extends BaseEvent {
    private UUID candidateId;
    private String skillName;
    private double demandPercentage;
    private String priority;
}
