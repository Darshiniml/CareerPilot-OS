package com.careerpilot.shared.events;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class InterviewCompletedEvent extends BaseEvent {
    private UUID userId;
}
