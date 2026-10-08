package com.careerpilot.backend.modules.ai.matching.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfile {
    /** The authenticated candidate this profile belongs to (set by the engine, never by clients). */
    private java.util.UUID candidateId;
    @Builder.Default
    private Set<String> skills = new HashSet<>();
    @Builder.Default
    private Set<String> technologies = new HashSet<>();
    private double totalExperienceYears;
    private String seniorityLevel;
    @Builder.Default
    private List<String> educationLevels = new ArrayList<>();
    @Builder.Default
    private Set<String> certifications = new HashSet<>();
    @Builder.Default
    private List<String> projectTechnologies = new ArrayList<>();
    @Builder.Default
    private List<String> projectDescriptions = new ArrayList<>();
    @Builder.Default
    private Set<String> languages = new HashSet<>();
    private double atsQuality;
    private double careerProgressionScore;
    private String summary;
    private String preferredWorkStyle;
    private Integer salaryMin;
    private Integer salaryMax;
    private String salaryCurrency;
    private String preferredEmploymentType;
    @Builder.Default
    private List<String> preferredLocations = new ArrayList<>();
}
