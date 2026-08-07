package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class EngineeringCultureMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "cultureMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        String companyCulture = company.getEngineeringCulture();
        String candidateWorkStyle = candidate.getPreferredWorkStyle();

        if (companyCulture == null || companyCulture.isBlank()) {
            return 100.0;
        }

        if (candidateWorkStyle == null || candidateWorkStyle.isBlank()) {
            return 50.0;
        }

        String normalizedCulture = normalizeToken(companyCulture);
        String normalizedWorkStyle = normalizeToken(candidateWorkStyle);

        double score = 50.0;

        if (normalizedCulture.contains("agile") && normalizedWorkStyle.contains("agile")) {
            score += 20.0;
        }
        if (normalizedCulture.contains("collaborative") && normalizedWorkStyle.contains("collaborative")) {
            score += 15.0;
        }
        if (normalizedCulture.contains("innovative") && normalizedWorkStyle.contains("innovative")) {
            score += 15.0;
        }
        if (normalizedCulture.contains("fast-paced") && normalizedWorkStyle.contains("fast-paced")) {
            score += 15.0;
        }
        if (normalizedCulture.contains("structured") && normalizedWorkStyle.contains("structured")) {
            score += 15.0;
        }
        if (normalizedCulture.contains("autonomous") && normalizedWorkStyle.contains("autonomous")) {
            score += 15.0;
        }
        if (normalizedCulture.contains("mentorship") && normalizedWorkStyle.contains("mentorship")) {
            score += 10.0;
        }

        return clampScore(score);
    }
}
