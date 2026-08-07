package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class ResponsibilityMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "responsibilityMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<String> responsibilities = job.getResponsibilities();
        String candidateSummary = candidate.getSummary();

        if (responsibilities == null || responsibilities.isEmpty()) {
            return 100.0;
        }

        if (candidateSummary == null || candidateSummary.isBlank()) {
            return 50.0;
        }

        double matchScore = 0.0;
        int matchCount = 0;

        String normalizedSummary = normalizeToken(candidateSummary);

        for (String resp : responsibilities) {
            if (resp == null || resp.isBlank()) continue;
            String normalizedResp = normalizeToken(resp);
            String[] keywords = normalizedResp.split("\\s+");

            int keywordMatches = 0;
            int totalKeywords = 0;

            for (String keyword : keywords) {
                if (keyword.length() < 3) continue;
                totalKeywords++;
                if (normalizedSummary.contains(keyword)) {
                    keywordMatches++;
                }
            }

            if (totalKeywords > 0) {
                matchScore += (keywordMatches * 100.0) / totalKeywords;
                matchCount++;
            }
        }

        if (matchCount == 0) return 50.0;
        return clampScore(matchScore / matchCount);
    }
}
