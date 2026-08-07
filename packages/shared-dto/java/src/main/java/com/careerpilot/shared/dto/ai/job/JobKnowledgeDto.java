package com.careerpilot.shared.dto.ai.job;

import com.careerpilot.shared.dto.ai.company.ExtractedValueDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobKnowledgeDto {
    private ExtractedValueDto<String> jobTitle;
    private ExtractedValueDto<String> normalizedJobTitle;
    private ExtractedValueDto<String> companyName;
    private UUID companyId;
    private ExtractedValueDto<String> department;
    private ExtractedValueDto<String> roleFamily;
    private ExtractedValueDto<String> careerTrack; // IC, Management, Hybrid
    private ExtractedValueDto<String> jobLevel;
    private ExtractedValueDto<String> reportingStructure;
    private ExtractedValueDto<String> declaredSeniority;
    private ExtractedValueDto<String> inferredSeniority;
    private ExtractedValueDto<String> employmentType;
    private ExtractedValueDto<String> workMode;
    private ExtractedValueDto<List<String>> locations;
    private ExtractedValueDto<String> salaryRange;
    private ExtractedValueDto<String> currency;
    private ExtractedValueDto<String> experienceRequired;
    private ExtractedValueDto<List<String>> educationRequirements;
    private ExtractedValueDto<List<String>> certifications;
    private List<ExtractedSkillDto> requiredSkills;
    private List<ExtractedSkillDto> preferredSkills;
    private ExtractedValueDto<List<String>> responsibilities;
    private ExtractedValueDto<List<String>> qualifications;
    private ExtractedValueDto<List<String>> benefits;
    private ExtractedValueDto<Boolean> visaSupport;
    private ExtractedValueDto<Boolean> relocationSupport;
    private ExtractedValueDto<String> travelRequirement;
    private ExtractedValueDto<String> workAuthorization;
    private ExtractedValueDto<String> securityClearance;
    private ExtractedValueDto<String> applicationDeadline;
    private ExtractedValueDto<String> postingDate;
    private ExtractedValueDto<String> sourcePlatform;
    private ExtractedValueDto<String> sourceUrl;
    private ExtractedValueDto<String> jobStatus; // Open, Closed, Expired
    private Map<String, Object> additionalProperties;
}
