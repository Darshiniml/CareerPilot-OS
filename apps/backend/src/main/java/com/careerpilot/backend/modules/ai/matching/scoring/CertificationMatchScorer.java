package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.matchedItems;

@Component
public class CertificationMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "certificationMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        Set<String> candidateCerts = candidate.getCertifications();
        Set<String> requiredCerts = job.getRequiredCertifications();

        if (requiredCerts == null || requiredCerts.isEmpty()) {
            return NOT_ASSESSABLE;
        }

        if (candidateCerts == null || candidateCerts.isEmpty()) {
            return 0.0;
        }

        Set<String> matched = matchedItems(candidateCerts, requiredCerts);
        double matchPercentage = (matched.size() * 100.0) / requiredCerts.size();

        return clampScore(matchPercentage);
    }
}
