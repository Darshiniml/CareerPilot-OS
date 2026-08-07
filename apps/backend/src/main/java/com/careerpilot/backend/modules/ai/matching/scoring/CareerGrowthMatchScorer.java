package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;

@Component
public class CareerGrowthMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "careerGrowthMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        double careerProgressionScore = candidate.getCareerProgressionScore();
        List<String> growthIndicators = company.getGrowthIndicators();
        String companySize = company.getCompanySize();

        double growthScore = computeGrowthScore(growthIndicators, companySize);
        double progressionScore = careerProgressionScore;

        return clampScore((growthScore * 0.6) + (progressionScore * 0.4));
    }

    private double computeGrowthScore(List<String> growthIndicators, String companySize) {
        if (growthIndicators == null || growthIndicators.isEmpty()) {
            return 50.0;
        }

        double score = 50.0;
        for (String indicator : growthIndicators) {
            if (indicator == null || indicator.isBlank()) continue;
            String normalized = indicator.toLowerCase();

            if (normalized.contains("funding") || normalized.contains("investment") || normalized.contains("series")) {
                score += 15.0;
            }
            if (normalized.contains("hiring") || normalized.contains("expansion") || normalized.contains("growing")) {
                score += 10.0;
            }
            if (normalized.contains("ipo") || normalized.contains("acquisition")) {
                score += 20.0;
            }
            if (normalized.contains("product launch") || normalized.contains("new market")) {
                score += 10.0;
            }
        }

        if (companySize != null && !companySize.isBlank()) {
            String normalizedSize = companySize.toLowerCase();
            if (normalizedSize.contains("startup") || normalizedSize.contains("1-10") || normalizedSize.contains("11-50")) {
                score += 10.0;
            }
        }

        return clampScore(score);
    }
}
