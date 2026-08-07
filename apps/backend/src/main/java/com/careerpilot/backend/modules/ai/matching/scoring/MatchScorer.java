package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;

public interface MatchScorer {
    String getFactorName();
    double score(CandidateProfile candidate, CompanyProfile company, JobProfile job);
}
