package com.careerpilot.shared.dto.ai.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedSkillDto {
    private String name;
    private Double confidence;
    private String importance; // REQUIRED, PREFERRED
    private String category;   // Backend, Frontend, Cloud, Soft Skills, etc.
}
