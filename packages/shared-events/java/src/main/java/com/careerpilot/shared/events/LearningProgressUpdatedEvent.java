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
public class LearningProgressUpdatedEvent extends BaseEvent {
    private UUID candidateId;
    private UUID learningPathId;
    private double progressPercentage;
}
