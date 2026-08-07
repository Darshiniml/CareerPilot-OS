package com.careerpilot.backend.modules.ai.matching.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobProfile {
    private UUID jobId;
    private UUID companyId;
    @Builder.Default
    private Set<String> requiredSkills = new HashSet<>();
    @Builder.Default
    private Set<String> preferredSkills = new HashSet<>();
    @Builder.Default
    private List<String> responsibilities = new ArrayList<>();
    private String seniority;
    private Double salaryMin;
    private Double salaryMax;
    private String salaryCurrency;
    @Builder.Default
    private List<String> locations = new ArrayList<>();
    private String employmentType;
    private String workMode;
    @Builder.Default
    private Set<String> requiredCertifications = new HashSet<>();
    @Builder.Default
    private List<String> educationRequirements = new ArrayList<>();
    @Builder.Default
    private Set<String> technologyStack = new HashSet<>();
    private String industry;
}
