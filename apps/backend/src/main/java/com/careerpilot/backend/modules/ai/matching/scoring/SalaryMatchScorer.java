package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;

@Component
public class SalaryMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "salaryMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        Double jobSalaryMin = job.getSalaryMin();
        Double jobSalaryMax = job.getSalaryMax();
        Integer candidateSalaryMin = candidate.getSalaryMin();
        Integer candidateSalaryMax = candidate.getSalaryMax();

        if (jobSalaryMin == null && jobSalaryMax == null) {
            return 100.0;
        }

        if (candidateSalaryMin == null && candidateSalaryMax == null) {
            return 50.0;
        }

        double jobRangeMid = computeJobRangeMid(jobSalaryMin, jobSalaryMax);
        double candidateRangeMid = computeCandidateRangeMid(candidateSalaryMin, candidateSalaryMax);

        if (jobRangeMid == 0 || candidateRangeMid == 0) {
            return 50.0;
        }

        double ratio = candidateRangeMid / jobRangeMid;

        if (ratio >= 0.9 && ratio <= 1.3) {
            return 100.0;
        } else if (ratio >= 0.7 && ratio <= 1.5) {
            return 80.0;
        } else if (ratio >= 0.5 && ratio <= 2.0) {
            return 60.0;
        } else {
            return 40.0;
        }
    }

    private double computeJobRangeMid(Double min, Double max) {
        if (min == null && max == null) return 0.0;
        if (min == null) return max;
        if (max == null) return min;
        return (min + max) / 2.0;
    }

    private double computeCandidateRangeMid(Integer min, Integer max) {
        if (min == null && max == null) return 0.0;
        if (min == null) return max;
        if (max == null) return min;
        return (min + max) / 2.0;
    }
}
