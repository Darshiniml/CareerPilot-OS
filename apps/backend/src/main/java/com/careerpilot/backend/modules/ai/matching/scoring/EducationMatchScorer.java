package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class EducationMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "educationMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<String> candidateEducation = candidate.getEducationLevels();
        List<String> jobRequirements = job.getEducationRequirements();

        if (jobRequirements == null || jobRequirements.isEmpty()) {
            return NOT_ASSESSABLE;
        }

        if (candidateEducation == null || candidateEducation.isEmpty()) {
            return 0.0;
        }

        double maxScore = 0.0;
        for (String req : jobRequirements) {
            double score = computeEducationMatch(candidateEducation, req);
            if (score > maxScore) {
                maxScore = score;
            }
        }

        return clampScore(maxScore);
    }

    private double computeEducationMatch(List<String> candidateEducation, String requirement) {
        String normalizedReq = normalizeToken(requirement);
        int requiredLevel = getEducationLevel(normalizedReq);

        int maxCandidateLevel = 0;
        for (String edu : candidateEducation) {
            String normalizedEdu = normalizeToken(edu);
            int level = getEducationLevel(normalizedEdu);
            if (level > maxCandidateLevel) {
                maxCandidateLevel = level;
            }
        }

        if (maxCandidateLevel >= requiredLevel) {
            return 100.0;
        }

        double gap = requiredLevel - maxCandidateLevel;
        return clampScore(100.0 - (gap * 30.0));
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
}
