package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.*;

@Component
public class TechnologyMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "technologyMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        Set<String> candidateTech = candidate.getTechnologies();
        Set<String> jobTech = job.getTechnologyStack();
        Set<String> companyTech = company.getTechnologyStack();

        if (jobTech.isEmpty() && companyTech.isEmpty()) {
            return 100.0;
        }

        double jobTechScore = 0.0;
        if (!jobTech.isEmpty()) {
            jobTechScore = jaccardSimilarity(candidateTech, jobTech);
        }

        double companyTechScore = 0.0;
        if (!companyTech.isEmpty()) {
            companyTechScore = jaccardSimilarity(candidateTech, companyTech);
        }

        if (jobTechScore > 0 && companyTechScore > 0) {
            return clampScore((jobTechScore * 0.7) + (companyTechScore * 0.3));
        } else if (jobTechScore > 0) {
            return jobTechScore;
        } else {
            return companyTechScore;
        }
    }
}
