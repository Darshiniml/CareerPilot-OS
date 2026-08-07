package com.careerpilot.backend.modules.ai.matching.gap;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.shared.dto.ai.matching.GapItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapAnalysisEngineTest {

    private GapAnalysisEngine gapAnalysisEngine;

    @BeforeEach
    void setUp() {
        gapAnalysisEngine = new GapAnalysisEngine();
    }

    @Test
    void testSkillGapsIdentified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("java", "spring"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(Set.of("java", "spring", "react", "kubernetes"))
                .preferredSkills(Set.of("docker"))
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        assertTrue(result.criticalGaps().size() > 0);
        assertTrue(result.recommendedImprovements().size() > 0);

        boolean hasReactGap = result.criticalGaps().stream()
                .anyMatch(gap -> "SKILL".equals(gap.getType()) && gap.getName().contains("react"));
        assertTrue(hasReactGap);
    }

    @Test
    void testExperienceGapIdentified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .totalExperienceYears(2.0)
                .seniorityLevel("JUNIOR")
                .build();

        JobProfile job = JobProfile.builder()
                .seniority("SENIOR")
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        boolean hasExperienceGap = result.criticalGaps().stream()
                .anyMatch(gap -> "EXPERIENCE".equals(gap.getType()));
        assertTrue(hasExperienceGap);
    }

    @Test
    void testCertificationGapIdentified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .certifications(Set.of("aws"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredCertifications(Set.of("aws", "gcp", "azure"))
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        boolean hasCertGap = result.criticalGaps().stream()
                .anyMatch(gap -> "CERTIFICATION".equals(gap.getType()));
        assertTrue(hasCertGap);
    }

    @Test
    void testNoGapsPerfectMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("java", "spring", "react"))
                .totalExperienceYears(5.0)
                .seniorityLevel("SENIOR")
                .certifications(Set.of("aws"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(Set.of("java", "spring"))
                .preferredSkills(Set.of("react"))
                .seniority("SENIOR")
                .requiredCertifications(Set.of("aws"))
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        assertEquals(0, result.criticalGaps().size());
    }

    @Test
    void testProjectGapIdentified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .projectTechnologies(List.of())
                .projectDescriptions(List.of())
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(Set.of("java", "spring"))
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        boolean hasProjectGap = result.recommendedImprovements().stream()
                .anyMatch(gap -> "PROJECT".equals(gap.getType()));
        assertTrue(hasProjectGap);
    }

    @Test
    void testEducationGapIdentified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .educationLevels(List.of("Bachelor"))
                .build();

        JobProfile job = JobProfile.builder()
                .educationRequirements(List.of("Master", "PhD"))
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        boolean hasEducationGap = result.recommendedImprovements().stream()
                .anyMatch(gap -> "EDUCATION".equals(gap.getType()));
        assertTrue(hasEducationGap);
    }

    @Test
    void testTechnologyGapIdentified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .technologies(Set.of("java", "spring"))
                .build();

        JobProfile job = JobProfile.builder()
                .technologyStack(Set.of("java", "spring", "react", "kubernetes"))
                .build();

        GapAnalysisEngine.GapAnalysisResult result = gapAnalysisEngine.analyzeGaps(
                candidate, CompanyProfile.builder().build(), job);

        boolean hasTechGap = result.recommendedImprovements().stream()
                .anyMatch(gap -> "TECHNOLOGY".equals(gap.getType()));
        assertTrue(hasTechGap);
    }
}
