package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class EmploymentTypeMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "employmentTypeMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        String jobEmploymentType = job.getEmploymentType();
        String candidateEmploymentType = candidate.getPreferredEmploymentType();

        if (jobEmploymentType == null || jobEmploymentType.isBlank()) {
            return 100.0;
        }

        if (candidateEmploymentType == null || candidateEmploymentType.isBlank()) {
            return 50.0;
        }

        String normalizedJob = normalizeToken(jobEmploymentType);
        String normalizedCandidate = normalizeToken(candidateEmploymentType);

        if (normalizedJob.equals(normalizedCandidate)) {
            return 100.0;
        }

        if (isCompatibleEmploymentType(normalizedJob, normalizedCandidate)) {
            return 80.0;
        }

        return 0.0;
    }

    private boolean isCompatibleEmploymentType(String jobType, String candidateType) {
        boolean jobFullTime = jobType.contains("full-time") || jobType.contains("fulltime") || jobType.contains("permanent");
        boolean jobContract = jobType.contains("contract") || jobType.contains("freelance");
        boolean jobPartTime = jobType.contains("part-time") || jobType.contains("parttime");
        boolean jobInternship = jobType.contains("intern") || jobType.contains("internship");

        boolean candidateFullTime = candidateType.contains("full-time") || candidateType.contains("fulltime") || candidateType.contains("permanent");
        boolean candidateContract = candidateType.contains("contract") || candidateType.contains("freelance");
        boolean candidatePartTime = candidateType.contains("part-time") || candidateType.contains("parttime");
        boolean candidateInternship = candidateType.contains("intern") || candidateType.contains("internship");

        if (jobFullTime && candidateFullTime) return true;
        if (jobContract && candidateContract) return true;
        if (jobPartTime && candidatePartTime) return true;
        if (jobInternship && candidateInternship) return true;

        if (jobFullTime && (candidateContract || candidatePartTime)) return true;
        if (jobContract && candidateFullTime) return true;

        return false;
    }
}
