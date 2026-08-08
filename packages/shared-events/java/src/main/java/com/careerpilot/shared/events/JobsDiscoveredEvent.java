package com.careerpilot.shared.events;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobsDiscoveredEvent extends BaseEvent {
    private UUID workflowId;
    private UUID userId;
    private List<UUID> jobIds;
}
