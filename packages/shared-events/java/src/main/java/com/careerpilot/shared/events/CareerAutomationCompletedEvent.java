package com.careerpilot.shared.events;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CareerAutomationCompletedEvent extends BaseEvent {
    private UUID workflowId;
    private UUID userId;
}
