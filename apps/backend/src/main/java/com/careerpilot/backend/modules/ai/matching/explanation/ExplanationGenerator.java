package com.careerpilot.backend.modules.ai.matching.explanation;

import com.careerpilot.backend.modules.ai.matching.aggregation.ScoreAggregator;
import com.careerpilot.backend.modules.ai.matching.gap.GapAnalysisEngine;
import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.shared.dto.ai.matching.GapItemDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExplanationGenerator {

    private final GapAnalysisEngine gapAnalysisEngine;

    public ExplanationGenerator(GapAnalysisEngine gapAnalysisEngine) {
        this.gapAnalysisEngine = gapAnalysisEngine;
    }

    public MatchExplanation generateExplanation(CandidateProfile candidate, CompanyProfile company, JobProfile job,
                                                 ScoreAggregator.AggregationResult aggregationResult) {
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        String summary = generateSummary(aggregationResult.overallScore());
        String detailedExplanation = generateDetailedExplanation(candidate, company, job, aggregationResult);

        identifyStrengths(candidate, company, job, aggregationResult.individualScores(), strengths);
        identifyWeaknesses(candidate, company, job, aggregationResult.individualScores(), weaknesses);

        GapAnalysisEngine.GapAnalysisResult gapAnalysis = gapAnalysisEngine.analyzeGaps(candidate, company, job);
        String gapSummary = generateGapSummary(gapAnalysis);

        return new MatchExplanation(
                summary,
                detailedExplanation,
                strengths,
                weaknesses,
                gapSummary,
                aggregationResult.overallScore(),
                aggregationResult.individualScores()
        );
    }

    private String generateSummary(double overallScore) {
        if (overallScore >= 90) {
            return "Excellent match - Strong alignment across all key factors.";
        } else if (overallScore >= 80) {
            return "Very good match - High compatibility with minor areas for improvement.";
        } else if (overallScore >= 70) {
            return "Good match - Solid fit with some gaps to address.";
        } else if (overallScore >= 60) {
            return "Moderate match - Requires addressing several key gaps.";
        } else if (overallScore >= 50) {
            return "Fair match - Significant gaps need attention.";
        } else {
            return "Poor match - Major misalignment across multiple factors.";
        }
    }

    private String generateDetailedExplanation(CandidateProfile candidate, CompanyProfile company, JobProfile job,
                                               ScoreAggregator.AggregationResult aggregationResult) {
        StringBuilder explanation = new StringBuilder();
        Map<String, Double> scores = aggregationResult.individualScores();

        explanation.append(String.format("Overall Match: %.1f%%\n\n", aggregationResult.overallScore()));

        explanation.append("Key Scores:\n");
        appendScore(explanation, "Skill Match", scores.get("skillMatch"));
        appendScore(explanation, "Experience Match", scores.get("experienceMatch"));
        appendScore(explanation, "Technology Match", scores.get("technologyMatch"));
        appendScore(explanation, "Location Match", scores.get("locationMatch"));
        appendScore(explanation, "Salary Match", scores.get("salaryMatch"));
        appendScore(explanation, "Culture Match", scores.get("cultureMatch"));
        appendScore(explanation, "Education Match", scores.get("educationMatch"));
        appendScore(explanation, "Career Growth", scores.get("careerGrowthMatch"));

        explanation.append("\n");

        if (candidate.getTotalExperienceYears() > 0) {
            explanation.append(String.format("Candidate Experience: %.1f years (%s)\n",
                    candidate.getTotalExperienceYears(), candidate.getSeniorityLevel()));
        }

        if (job.getSeniority() != null && !job.getSeniority().isBlank()) {
            explanation.append(String.format("Job Seniority: %s\n", job.getSeniority()));
        }

        if (job.getLocations() != null && !job.getLocations().isEmpty()) {
            explanation.append(String.format("Location: %s\n", String.join(", ", job.getLocations())));
        }

        if (job.getWorkMode() != null && !job.getWorkMode().isBlank()) {
            explanation.append(String.format("Work Mode: %s\n", job.getWorkMode()));
        }

        return explanation.toString();
    }

    private void appendScore(StringBuilder builder, String label, Double score) {
        if (score != null) {
            builder.append(String.format("- %s: %.1f%%\n", label, score));
        }
    }

    private void identifyStrengths(CandidateProfile candidate, CompanyProfile company, JobProfile job,
                                   Map<String, Double> scores, List<String> strengths) {
        if (scores.getOrDefault("skillMatch", 0.0) >= 80) {
            strengths.add("Strong skill alignment with job requirements");
        }

        if (scores.getOrDefault("experienceMatch", 0.0) >= 80) {
            strengths.add(String.format("Relevant experience level (%.1f years) for the role", candidate.getTotalExperienceYears()));
        }

        if (scores.getOrDefault("technologyMatch", 0.0) >= 80) {
            strengths.add("Excellent technology stack match");
        }

        if (scores.getOrDefault("locationMatch", 0.0) >= 90) {
            strengths.add("Perfect location match");
        }

        if (scores.getOrDefault("salaryMatch", 0.0) >= 80) {
            strengths.add("Salary expectations align with job offer");
        }

        if (scores.getOrDefault("cultureMatch", 0.0) >= 80) {
            strengths.add("Strong cultural fit with company");
        }

        if (scores.getOrDefault("educationMatch", 0.0) >= 90) {
            strengths.add("Education requirements fully met");
        }

        if (scores.getOrDefault("careerGrowthMatch", 0.0) >= 80) {
            strengths.add("Excellent career growth opportunities");
        }

        if (candidate.getAtsQuality() >= 80) {
            strengths.add("High resume quality score");
        }

        if (candidate.getCareerProgressionScore() >= 70) {
            strengths.add("Strong career progression trajectory");
        }

        if (strengths.isEmpty()) {
            strengths.add("Several positive factors contribute to this match");
        }
    }

    private void identifyWeaknesses(CandidateProfile candidate, CompanyProfile company, JobProfile job,
                                    Map<String, Double> scores, List<String> weaknesses) {
        if (scores.getOrDefault("skillMatch", 0.0) < 60) {
            weaknesses.add("Significant skill gaps need to be addressed");
        }

        if (scores.getOrDefault("experienceMatch", 0.0) < 60) {
            weaknesses.add("Experience level may not meet job requirements");
        }

        if (scores.getOrDefault("technologyMatch", 0.0) < 60) {
            weaknesses.add("Technology stack mismatch");
        }

        if (scores.getOrDefault("locationMatch", 0.0) < 50) {
            weaknesses.add("Location preferences don't align with job location");
        }

        if (scores.getOrDefault("salaryMatch", 0.0) < 50) {
            weaknesses.add("Salary expectations may not align with job offer");
        }

        if (scores.getOrDefault("cultureMatch", 0.0) < 60) {
            weaknesses.add("Cultural fit may need consideration");
        }

        if (scores.getOrDefault("educationMatch", 0.0) < 60) {
            weaknesses.add("Education requirements not fully met");
        }

        if (candidate.getAtsQuality() < 60) {
            weaknesses.add("Resume quality could be improved for better ATS performance");
        }

        if (weaknesses.isEmpty()) {
            weaknesses.add("No major weaknesses identified");
        }
    }

    private String generateGapSummary(GapAnalysisEngine.GapAnalysisResult gapAnalysis) {
        StringBuilder summary = new StringBuilder();

        if (!gapAnalysis.criticalGaps().isEmpty()) {
            summary.append("Critical Gaps:\n");
            for (GapItemDto gap : gapAnalysis.criticalGaps()) {
                summary.append(String.format("- %s: %s\n", gap.getType(), gap.getName()));
            }
            summary.append("\n");
        }

        if (!gapAnalysis.recommendedImprovements().isEmpty()) {
            summary.append("Recommended Improvements:\n");
            for (GapItemDto gap : gapAnalysis.recommendedImprovements()) {
                summary.append(String.format("- %s: %s\n", gap.getType(), gap.getName()));
            }
            summary.append("\n");
        }

        if (!gapAnalysis.optionalImprovements().isEmpty()) {
            summary.append("Optional Enhancements:\n");
            for (GapItemDto gap : gapAnalysis.optionalImprovements()) {
                summary.append(String.format("- %s: %s\n", gap.getType(), gap.getName()));
            }
        }

        if (summary.length() == 0) {
            summary.append("No significant gaps identified - excellent alignment!");
        }

        return summary.toString();
    }

    public record MatchExplanation(
            String summary,
            String detailedExplanation,
            List<String> strengths,
            List<String> weaknesses,
            String gapSummary,
            double overallScore,
            Map<String, Double> individualScores
    ) {}
}
