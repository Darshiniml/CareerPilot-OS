package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class RemotePreferenceMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "remotePreferenceMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        String jobWorkMode = job.getWorkMode();
        String candidateWorkStyle = candidate.getPreferredWorkStyle();
        String companyRemotePolicy = company.getRemotePolicy();

        if (jobWorkMode == null || jobWorkMode.isBlank()) {
            return NOT_ASSESSABLE;
        }

        boolean jobIsRemote = isRemoteWorkMode(jobWorkMode);
        boolean candidateWantsRemote = candidateWantsRemote(candidateWorkStyle);
        boolean companySupportsRemote = companySupportsRemote(companyRemotePolicy);

        if (jobIsRemote) {
            if (candidateWantsRemote) {
                return 100.0;
            }
            return clampScore(companySupportsRemote ? 80.0 : 60.0);
        } else {
            if (!candidateWantsRemote) {
                return 100.0;
            }
            return 40.0;
        }
    }

    private boolean isRemoteWorkMode(String workMode) {
        String normalized = normalizeToken(workMode);
        return normalized.contains("remote") || normalized.contains("wfh") || normalized.contains("work from home");
    }

    private boolean candidateWantsRemote(String workStyle) {
        if (workStyle == null || workStyle.isBlank()) return false;
        String normalized = normalizeToken(workStyle);
        return normalized.contains("remote") || normalized.contains("wfh") || normalized.contains("work from home");
    }

    private boolean companySupportsRemote(String remotePolicy) {
        if (remotePolicy == null || remotePolicy.isBlank()) return false;
        String normalized = normalizeToken(remotePolicy);
        return normalized.contains("remote") || normalized.contains("hybrid") || normalized.contains("flexible");
    }
}
