package com.careerpilot.shared.dto.ai.resume;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillWithConfidenceDto {
    private String skill;
    private double confidence;
    private String source;
    private String category;
}
