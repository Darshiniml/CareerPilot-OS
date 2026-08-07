package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.*;

@Component
public class SkillMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "skillMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        Set<String> required = job.getRequiredSkills();
        if (required.isEmpty()) {
            return clampScore(jaccardSimilarity(candidate.getSkills(), job.getPreferredSkills()));
        }
        Set<String> matched = matchedItems(candidate.getSkills(), required);
        double requiredScore = (matched.size() * 100.0) / required.size();
        Set<String> preferredMatched = matchedItems(candidate.getSkills(), job.getPreferredSkills());
        double preferredBonus = job.getPreferredSkills().isEmpty() ? 0
                : (preferredMatched.size() * 10.0) / job.getPreferredSkills().size();
        return clampScore(requiredScore + preferredBonus);
    }
}
