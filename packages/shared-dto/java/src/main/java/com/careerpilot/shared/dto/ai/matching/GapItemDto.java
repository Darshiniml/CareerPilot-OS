package com.careerpilot.shared.dto.ai.matching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapItemDto {
    private String type;
    private String name;
    private String category;
    private String description;
    private String severity;
    private String suggestedAction;
}
