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
public class ExperienceIntelligenceDto {
    private double totalYearsExperience;
    private String currentRole;
    private List<String> careerProgression;
    private List<String> employmentGaps;
    private List<String> overlappingPeriods;
}
