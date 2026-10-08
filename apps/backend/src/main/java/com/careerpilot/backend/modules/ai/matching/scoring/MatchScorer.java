package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;

public interface MatchScorer {
    /**
     * Returned when the data needed to judge this factor is missing (e.g. the job states no salary,
     * the candidate set no preference). The aggregator excludes such factors instead of counting an
     * invented score, and reports them as not assessed.
     */
    double NOT_ASSESSABLE = Double.NaN;

    String getFactorName();
    double score(CandidateProfile candidate, CompanyProfile company, JobProfile job);
}
