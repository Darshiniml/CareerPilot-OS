package com.careerpilot.shared.dto.ai.matching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationItemDto {
    private String type;
    private String action;
    private String category;
    private String priority;
    private String description;
    private String rationale;
    private String estimatedEffort;
}
