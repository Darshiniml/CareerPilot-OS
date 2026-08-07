package com.careerpilot.shared.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReceivedEvent extends BaseEvent {
    private UUID interviewId;
    private UUID applicationId;
    private UUID userId;
    private Instant scheduledTime;
    private String interviewType;
}
