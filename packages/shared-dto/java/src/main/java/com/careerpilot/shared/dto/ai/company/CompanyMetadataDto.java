package com.careerpilot.shared.dto.ai.company;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyMetadataDto {
    private List<String> industries;
    private String employeeRange;
    private String remotePolicy;
    private List<String> technologyCategories;
    private String primaryLanguage;
    private List<String> primaryTechnologyStack;
    private List<String> secondaryTechnologies;
    private Double backendFocus;
    private Double frontendFocus;
    private String cloudMaturity; // HIGH, MEDIUM, LOW
    private String aiAdoption; // HIGH, MEDIUM, LOW
    private String devOpsMaturity; // HIGH, MEDIUM, LOW
}
