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
public class CompanyProfile {
    private UUID companyId;
    @Builder.Default
    private Set<String> technologyStack = new HashSet<>();
    @Builder.Default
    private List<String> industries = new ArrayList<>();
    private String engineeringCulture;
    @Builder.Default
    private List<String> hiringSignals = new ArrayList<>();
    private String companySize;
    private String remotePolicy;
    @Builder.Default
    private List<String> growthIndicators = new ArrayList<>();
    @Builder.Default
    private List<String> benefits = new ArrayList<>();
    @Builder.Default
    private List<String> learningOpportunities = new ArrayList<>();
}
