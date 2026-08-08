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
public class LearningRecommendationGeneratedEvent extends BaseEvent {
    private UUID candidateId;
    private String skillName;
    private String recommendation;
    private String evidence;
}
