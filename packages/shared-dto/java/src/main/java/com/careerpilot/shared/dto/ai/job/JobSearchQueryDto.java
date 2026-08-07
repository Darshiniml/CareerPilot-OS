package com.careerpilot.shared.dto.ai.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchQueryDto {
    private String query;
    private String industryFilter;
    private String technologyFilter;
    private String locationFilter;
    private String companySizeFilter;
    private String remotePolicyFilter;
    private Double salaryMin;
    private Double salaryMax;
    private String employmentType;
    private String seniority;
    private String roleFamily;
    private String workMode;
    private Integer experienceMin;
    private Integer experienceMax;
    private Integer page;
    private Integer size;
    private Double scoreThreshold;
}
