package com.careerpilot.shared.events;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class CopilotRecommendationGeneratedEvent extends BaseEvent {
    private UUID userId;
    private String title;
}
