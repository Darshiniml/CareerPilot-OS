package com.careerpilot.shared.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobDiscoveredEvent extends BaseEvent {
    private UUID jobId;
    private String title;
    private String companyName;
    private String location;
    private String description;
    private List<String> requiredSkills;
}
