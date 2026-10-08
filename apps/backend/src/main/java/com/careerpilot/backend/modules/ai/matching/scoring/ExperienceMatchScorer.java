package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;

@Component
public class ExperienceMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "experienceMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        double candidateYears = candidate.getTotalExperienceYears();
        String jobSeniority = job.getSeniority();
        String candidateSeniority = candidate.getSeniorityLevel();

        if (jobSeniority == null || jobSeniority.isBlank()) {
            return NOT_ASSESSABLE;
        }

        double seniorityScore = computeSeniorityMatch(candidateSeniority, jobSeniority);
        double yearsScore = computeYearsMatch(candidateYears, jobSeniority);

        return clampScore((seniorityScore * 0.6) + (yearsScore * 0.4));
    }

    private double computeSeniorityMatch(String candidateSeniority, String jobSeniority) {
        String normalizedCandidate = normalizeSeniority(candidateSeniority);
        String normalizedJob = normalizeSeniority(jobSeniority);

        if (normalizedCandidate.equals(normalizedJob)) {
            return 100.0;
        }

        int candidateLevel = getSeniorityLevel(normalizedCandidate);
        int jobLevel = getSeniorityLevel(normalizedJob);

        if (candidateLevel >= jobLevel) {
            double overqualificationPenalty = (candidateLevel - jobLevel) * 10.0;
            return clampScore(100.0 - overqualificationPenalty);
        } else {
            double underqualificationPenalty = (jobLevel - candidateLevel) * 25.0;
            return clampScore(100.0 - underqualificationPenalty);
        }
    }

    private double computeYearsMatch(double candidateYears, String jobSeniority) {
        String normalized = normalizeSeniority(jobSeniority);
        double minRequired = getMinYearsForSeniority(normalized);
        double maxIdeal = getMaxYearsForSeniority(normalized);

        if (candidateYears >= minRequired && candidateYears <= maxIdeal) {
            return 100.0;
        }

        if (candidateYears < minRequired) {
            double gap = minRequired - candidateYears;
            return clampScore(100.0 - (gap * 20.0));
        }

        if (candidateYears > maxIdeal) {
            double overage = candidateYears - maxIdeal;
            return clampScore(100.0 - (overage * 5.0));
        }

        return 100.0;
    }

    private String normalizeSeniority(String seniority) {
        if (seniority == null) return "mid";
        String s = seniority.toLowerCase();
        if (s.contains("intern") || s.contains("fresher")) return "fresher";
        if (s.contains("junior") || s.contains("associate")) return "junior";
        if (s.contains("mid") || s.contains("middle")) return "mid";
        if (s.contains("senior") || s.contains("sr")) return "senior";
        if (s.contains("lead") || s.contains("staff")) return "lead";
        if (s.contains("principal") || s.contains("architect")) return "principal";
        if (s.contains("director") || s.contains("manager")) return "director";
        return "mid";
    }

    private int getSeniorityLevel(String normalizedSeniority) {
        return switch (normalizedSeniority) {
            case "fresher" -> 0;
            case "junior" -> 1;
            case "mid" -> 2;
            case "senior" -> 3;
            case "lead" -> 4;
            case "principal" -> 5;
            case "director" -> 6;
            default -> 2;
        };
    }

    private double getMinYearsForSeniority(String normalizedSeniority) {
        return switch (normalizedSeniority) {
            case "fresher" -> 0.0;
            case "junior" -> 1.0;
            case "mid" -> 3.0;
            case "senior" -> 5.0;
            case "lead" -> 7.0;
            case "principal" -> 10.0;
            case "director" -> 12.0;
            default -> 3.0;
        };
    }

    private double getMaxYearsForSeniority(String normalizedSeniority) {
        return switch (normalizedSeniority) {
            case "fresher" -> 1.0;
            case "junior" -> 3.0;
            case "mid" -> 5.0;
            case "senior" -> 8.0;
            case "lead" -> 12.0;
            case "principal" -> 15.0;
            case "director" -> 20.0;
            default -> 10.0;
        };
    }
}
