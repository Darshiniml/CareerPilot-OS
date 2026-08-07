package com.careerpilot.backend.modules.ai.matching.recommendation;

import com.careerpilot.backend.modules.ai.matching.gap.GapAnalysisEngine;
import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.shared.dto.ai.matching.GapItemDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationItemDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RecommendationEngine {

    private final GapAnalysisEngine gapAnalysisEngine;

    public RecommendationEngine(GapAnalysisEngine gapAnalysisEngine) {
        this.gapAnalysisEngine = gapAnalysisEngine;
    }

    public List<RecommendationItemDto> generateRecommendations(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        GapAnalysisEngine.GapAnalysisResult gapAnalysis = gapAnalysisEngine.analyzeGaps(candidate, company, job);
        List<RecommendationItemDto> recommendations = new ArrayList<>();

        generateSkillRecommendations(gapAnalysis, recommendations);
        generateTechnologyRecommendations(gapAnalysis, recommendations);
        generateExperienceRecommendations(gapAnalysis, recommendations);
        generateCertificationRecommendations(gapAnalysis, recommendations);
        generateEducationRecommendations(gapAnalysis, recommendations);
        generateProjectRecommendations(gapAnalysis, recommendations);
        generateResumeRecommendations(candidate, job, recommendations);

        return recommendations;
    }

    private void generateSkillRecommendations(GapAnalysisEngine.GapAnalysisResult gapAnalysis, List<RecommendationItemDto> recommendations) {
        for (GapItemDto gap : gapAnalysis.criticalGaps()) {
            if ("SKILL".equals(gap.getType())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("SKILL_ACQUISITION")
                        .priority("HIGH")
                        .action("Learn " + gap.getName())
                        .description(gap.getDescription())
                        .estimatedEffort("2-8 weeks")
                        .build());
            }
        }

        for (GapItemDto gap : gapAnalysis.recommendedImprovements()) {
            if ("SKILL".equals(gap.getType())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("SKILL_ENHANCEMENT")
                        .priority("MEDIUM")
                        .action("Learn " + gap.getName())
                        .description(gap.getDescription())
                        .estimatedEffort("1-4 weeks")
                        .build());
            }
        }
    }

    private void generateTechnologyRecommendations(GapAnalysisEngine.GapAnalysisResult gapAnalysis, List<RecommendationItemDto> recommendations) {
        for (GapItemDto gap : gapAnalysis.recommendedImprovements()) {
            if ("TECHNOLOGY".equals(gap.getType())) {
                String tech = gap.getName();
                recommendations.add(RecommendationItemDto.builder()
                        .type("TECHNOLOGY_EXPERIENCE")
                        .priority("MEDIUM")
                        .action("Gain experience with " + tech)
                        .description("Build a project using " + tech + " to demonstrate practical knowledge")
                        .estimatedEffort("4-12 weeks")
                        .build());
            }
        }
    }

    private void generateExperienceRecommendations(GapAnalysisEngine.GapAnalysisResult gapAnalysis, List<RecommendationItemDto> recommendations) {
        for (GapItemDto gap : gapAnalysis.criticalGaps()) {
            if ("EXPERIENCE".equals(gap.getType())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("EXPERIENCE_GAIN")
                        .priority("HIGH")
                        .action("Gain relevant work experience")
                        .description(gap.getDescription())
                        .estimatedEffort("6-24 months")
                        .build());
            }
        }

        for (GapItemDto gap : gapAnalysis.recommendedImprovements()) {
            if ("EXPERIENCE".equals(gap.getType())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("EXPERIENCE_ENHANCEMENT")
                        .priority("MEDIUM")
                        .action("Seek additional experience")
                        .description(gap.getDescription())
                        .estimatedEffort("3-12 months")
                        .build());
            }
        }
    }

    private void generateCertificationRecommendations(GapAnalysisEngine.GapAnalysisResult gapAnalysis, List<RecommendationItemDto> recommendations) {
        for (GapItemDto gap : gapAnalysis.criticalGaps()) {
            if ("CERTIFICATION".equals(gap.getType())) {
                String cert = gap.getName();
                recommendations.add(RecommendationItemDto.builder()
                        .type("CERTIFICATION")
                        .priority("HIGH")
                        .action("Obtain " + cert + " certification")
                        .description("Earn the " + cert + " certification to meet job requirements")
                        .estimatedEffort("4-12 weeks")
                        .build());
            }
        }
    }

    private void generateEducationRecommendations(GapAnalysisEngine.GapAnalysisResult gapAnalysis, List<RecommendationItemDto> recommendations) {
        for (GapItemDto gap : gapAnalysis.recommendedImprovements()) {
            if ("EDUCATION".equals(gap.getType())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("EDUCATION")
                        .priority("MEDIUM")
                        .action("Pursue higher education")
                        .description(gap.getDescription())
                        .estimatedEffort("1-4 years")
                        .build());
            }
        }
    }

    private void generateProjectRecommendations(GapAnalysisEngine.GapAnalysisResult gapAnalysis, List<RecommendationItemDto> recommendations) {
        for (GapItemDto gap : gapAnalysis.recommendedImprovements()) {
            if ("PROJECT".equals(gap.getType()) && "Project Portfolio".equals(gap.getName())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("PROJECT_BUILD")
                        .priority("HIGH")
                        .action("Build relevant projects")
                        .description("Create 2-3 projects that demonstrate your skills and align with job requirements")
                        .estimatedEffort("8-16 weeks")
                        .build());
            }
        }

        for (GapItemDto gap : gapAnalysis.optionalImprovements()) {
            if ("PROJECT".equals(gap.getType()) && "Project Details".equals(gap.getName())) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("PROJECT_ENHANCEMENT")
                        .priority("LOW")
                        .action("Enhance project descriptions")
                        .description("Add detailed descriptions, technologies used, and outcomes to existing projects")
                        .estimatedEffort("1-2 weeks")
                        .build());
            }
        }
    }

    private void generateResumeRecommendations(CandidateProfile candidate, JobProfile job, List<RecommendationItemDto> recommendations) {
        if (candidate.getSummary() == null || candidate.getSummary().isBlank()) {
            recommendations.add(RecommendationItemDto.builder()
                    .type("RESUME_IMPROVEMENT")
                    .priority("MEDIUM")
                    .action("Add professional summary")
                    .description("Create a compelling professional summary that highlights your key skills and career goals")
                    .estimatedEffort("1-2 hours")
                    .build());
        }

        if (candidate.getAtsQuality() < 70.0) {
            recommendations.add(RecommendationItemDto.builder()
                    .type("RESUME_OPTIMIZATION")
                    .priority("HIGH")
                    .action("Improve ATS score")
                    .description("Optimize your resume for ATS by adding relevant keywords and improving formatting")
                    .estimatedEffort("2-4 hours")
                    .build());
        }

        if (candidate.getCareerProgressionScore() < 60.0) {
            recommendations.add(RecommendationItemDto.builder()
                    .type("CAREER_PROGRESS")
                    .priority("MEDIUM")
                    .action("Highlight career progression")
                    .description("Emphasize your career growth and achievements in your resume")
                    .estimatedEffort("1-2 hours")
                    .build());
        }

        if (job.getRequiredSkills() != null && !job.getRequiredSkills().isEmpty()) {
            if (candidate.getSkills() == null || candidate.getSkills().isEmpty()) {
                recommendations.add(RecommendationItemDto.builder()
                        .type("RESUME_SKILLS")
                        .priority("HIGH")
                        .action("Add skills section")
                        .description("Add a comprehensive skills section to your resume highlighting relevant technical and soft skills")
                        .estimatedEffort("1-2 hours")
                        .build());
            }
        }
    }
}
