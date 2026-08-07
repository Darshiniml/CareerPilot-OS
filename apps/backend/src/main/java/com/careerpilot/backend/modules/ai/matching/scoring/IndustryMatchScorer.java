package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class IndustryMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "industryMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<String> companyIndustries = company.getIndustries();
        String jobIndustry = job.getIndustry();

        if (companyIndustries == null || companyIndustries.isEmpty()) {
            return 100.0;
        }

        if (jobIndustry != null && !jobIndustry.isBlank()) {
            String normalizedJobIndustry = normalizeToken(jobIndustry);
            for (String industry : companyIndustries) {
                String normalizedIndustry = normalizeToken(industry);
                if (normalizedIndustry.contains(normalizedJobIndustry) || normalizedJobIndustry.contains(normalizedIndustry)) {
                    return 100.0;
                }
            }
        }

        return 50.0;
    }
}
