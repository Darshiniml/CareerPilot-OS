package com.careerpilot.shared.dto.ai.resume;

import com.careerpilot.shared.dto.profile.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeKnowledgeDto {
    private Map<String, Object> personalInformation;
    private String summary;
    private List<SkillWithConfidenceDto> skills;
    private List<EducationDto> education;
    private List<ExperienceDto> experience;
    private List<ProjectIntelligenceDto> projects;
    private List<CertificationDto> certifications;
    private List<String> achievements;
    private List<String> languages;
    private List<String> publications;
    private List<String> awards;
    private List<String> references;
}
