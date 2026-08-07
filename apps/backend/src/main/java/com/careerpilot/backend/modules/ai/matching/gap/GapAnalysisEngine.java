package com.careerpilot.backend.modules.ai.matching.gap;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.shared.dto.ai.matching.GapItemDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.missingItems;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class GapAnalysisEngine {

    public GapAnalysisResult analyzeGaps(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<GapItemDto> criticalGaps = new ArrayList<>();
        List<GapItemDto> recommendedImprovements = new ArrayList<>();
        List<GapItemDto> optionalImprovements = new ArrayList<>();

        analyzeSkillGaps(candidate, job, criticalGaps, recommendedImprovements);
        analyzeExperienceGaps(candidate, job, criticalGaps, recommendedImprovements);
        analyzeCertificationGaps(candidate, job, criticalGaps, recommendedImprovements);
        analyzeEducationGaps(candidate, job, recommendedImprovements, optionalImprovements);
        analyzeTechnologyGaps(candidate, job, recommendedImprovements, optionalImprovements);
        analyzeProjectGaps(candidate, job, recommendedImprovements, optionalImprovements);
        analyzeResponsibilityGaps(candidate, job, optionalImprovements);

        return new GapAnalysisResult(criticalGaps, recommendedImprovements, optionalImprovements);
    }

    private void analyzeSkillGaps(CandidateProfile candidate, JobProfile job,
                                   List<GapItemDto> criticalGaps, List<GapItemDto> recommendedImprovements) {
        Set<String> missingRequired = missingItems(candidate.getSkills(), job.getRequiredSkills());
        Set<String> missingPreferred = missingItems(candidate.getSkills(), job.getPreferredSkills());

        for (String skill : missingRequired) {
            criticalGaps.add(GapItemDto.builder()
                    .type("SKILL")
                    .name(skill)
                    .severity("CRITICAL")
                    .description("Required skill: " + skill)
                    .build());
        }

        for (String skill : missingPreferred) {
            recommendedImprovements.add(GapItemDto.builder()
                    .type("SKILL")
                    .name(skill)
                    .severity("RECOMMENDED")
                    .description("Preferred skill: " + skill)
                    .build());
        }
    }

    private void analyzeExperienceGaps(CandidateProfile candidate, JobProfile job,
                                       List<GapItemDto> criticalGaps, List<GapItemDto> recommendedImprovements) {
        double candidateYears = candidate.getTotalExperienceYears();
        String jobSeniority = job.getSeniority();

        if (jobSeniority != null && !jobSeniority.isBlank()) {
            double minRequired = getMinYearsForSeniority(jobSeniority);
            if (candidateYears < minRequired) {
                double gap = minRequired - candidateYears;
                criticalGaps.add(GapItemDto.builder()
                        .type("EXPERIENCE")
                        .name("Years of Experience")
                        .severity("CRITICAL")
                        .description(String.format("Requires %.1f more years of experience for %s role", gap, jobSeniority))
                        .build());
            } else if (candidateYears < minRequired + 2) {
                recommendedImprovements.add(GapItemDto.builder()
                        .type("EXPERIENCE")
                        .name("Years of Experience")
                        .severity("RECOMMENDED")
                        .description(String.format("Additional experience would strengthen candidacy for %s role", jobSeniority))
                        .build());
            }
        }
    }

    private void analyzeCertificationGaps(CandidateProfile candidate, JobProfile job,
                                           List<GapItemDto> criticalGaps, List<GapItemDto> recommendedImprovements) {
        Set<String> missingCerts = missingItems(candidate.getCertifications(), job.getRequiredCertifications());

        for (String cert : missingCerts) {
            criticalGaps.add(GapItemDto.builder()
                    .type("CERTIFICATION")
                    .name(cert)
                    .severity("CRITICAL")
                    .description("Required certification: " + cert)
                    .build());
        }
    }

    private void analyzeEducationGaps(CandidateProfile candidate, JobProfile job,
                                       List<GapItemDto> recommendedImprovements, List<GapItemDto> optionalImprovements) {
        if (job.getEducationRequirements() == null || job.getEducationRequirements().isEmpty()) {
            return;
        }

        int candidateLevel = getMaxEducationLevel(candidate.getEducationLevels());
        int requiredLevel = getMinRequiredEducationLevel(job.getEducationRequirements());

        if (candidateLevel < requiredLevel) {
            recommendedImprovements.add(GapItemDto.builder()
                    .type("EDUCATION")
                    .name("Education Level")
                    .severity("RECOMMENDED")
                    .description("Consider pursuing higher education to meet requirements")
                    .build());
        }
    }

    private void analyzeTechnologyGaps(CandidateProfile candidate, JobProfile job,
                                        List<GapItemDto> recommendedImprovements, List<GapItemDto> optionalImprovements) {
        Set<String> candidateTech = new HashSet<>(candidate.getTechnologies());
        Set<String> jobTech = new HashSet<>(job.getTechnologyStack());

        Set<String> missingTech = new HashSet<>(jobTech);
        missingTech.removeAll(candidateTech);

        for (String tech : missingTech) {
            recommendedImprovements.add(GapItemDto.builder()
                    .type("TECHNOLOGY")
                    .name(tech)
                    .severity("RECOMMENDED")
                    .description("Gain experience with: " + tech)
                    .build());
        }
    }

    private void analyzeProjectGaps(CandidateProfile candidate, JobProfile job,
                                     List<GapItemDto> recommendedImprovements, List<GapItemDto> optionalImprovements) {
        if (candidate.getProjectTechnologies() == null || candidate.getProjectTechnologies().isEmpty()) {
            recommendedImprovements.add(GapItemDto.builder()
                    .type("PROJECT")
                    .name("Project Portfolio")
                    .severity("RECOMMENDED")
                    .description("Add relevant projects to demonstrate practical skills")
                    .build());
        }

        if (candidate.getProjectDescriptions() == null || candidate.getProjectDescriptions().isEmpty()) {
            optionalImprovements.add(GapItemDto.builder()
                    .type("PROJECT")
                    .name("Project Details")
                    .severity("OPTIONAL")
                    .description("Add detailed descriptions to existing projects")
                    .build());
        }
    }

    private void analyzeResponsibilityGaps(CandidateProfile candidate, JobProfile job,
                                            List<GapItemDto> optionalImprovements) {
        if (job.getResponsibilities() != null && !job.getResponsibilities().isEmpty()) {
            if (candidate.getSummary() == null || candidate.getSummary().isBlank()) {
                optionalImprovements.add(GapItemDto.builder()
                        .type("RESPONSIBILITY")
                        .name("Professional Summary")
                        .severity("OPTIONAL")
                        .description("Add a professional summary aligning with job responsibilities")
                        .build());
            }
        }
    }

    private double getMinYearsForSeniority(String seniority) {
        String normalized = normalizeToken(seniority);
        if (normalized.contains("fresher") || normalized.contains("intern")) return 0.0;
        if (normalized.contains("junior") || normalized.contains("associate")) return 1.0;
        if (normalized.contains("mid") || normalized.contains("middle")) return 3.0;
        if (normalized.contains("senior") || normalized.contains("sr")) return 5.0;
        if (normalized.contains("lead") || normalized.contains("staff")) return 7.0;
        if (normalized.contains("principal") || normalized.contains("architect")) return 10.0;
        if (normalized.contains("director") || normalized.contains("manager")) return 12.0;
        return 3.0;
    }

    private int getMaxEducationLevel(List<String> educationLevels) {
        int maxLevel = 0;
        for (String edu : educationLevels) {
            String normalized = normalizeToken(edu);
            int level = getEducationLevel(normalized);
            if (level > maxLevel) {
                maxLevel = level;
            }
        }
        return maxLevel;
    }

    private int getMinRequiredEducationLevel(List<String> requirements) {
        int minLevel = Integer.MAX_VALUE;
        for (String req : requirements) {
            String normalized = normalizeToken(req);
            int level = getEducationLevel(normalized);
            if (level < minLevel) {
                minLevel = level;
            }
        }
        return minLevel == Integer.MAX_VALUE ? 0 : minLevel;
    }

    private int getEducationLevel(String normalizedEducation) {
        String e = normalizedEducation.toLowerCase();
        if (e.contains("phd") || e.contains("doctorate")) return 5;
        if (e.contains("master") || e.contains("mtech") || e.contains("ms")) return 4;
        if (e.contains("bachelor") || e.contains("btech") || e.contains("bs") || e.contains("be")) return 3;
        if (e.contains("associate") || e.contains("diploma")) return 2;
        if (e.contains("high school") || e.contains("12th") || e.contains("10+2")) return 1;
        return 0;
    }

    public record GapAnalysisResult(
            List<GapItemDto> criticalGaps,
            List<GapItemDto> recommendedImprovements,
            List<GapItemDto> optionalImprovements
    ) {}
}
