package com.careerpilot.backend.modules.application.services;

import com.careerpilot.shared.events.BaseEvent;
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
public class ApplicationCreatedEvent extends BaseEvent {
    private UUID applicationId;
    private UUID userId;
    private UUID jobId;
    private String status;
}
