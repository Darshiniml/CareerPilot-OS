package com.careerpilot.shared.dto.ai.resume;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectIntelligenceDto {
    private String name;
    private String description;
    private List<String> technologies;
    private String domain;
    private String role;
    private double complexityScore;
    private int technologyDiversityCount;
    private boolean aiProjectFlag;
    private boolean openSourceFlag;
}
