package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class LocationMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "locationMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<String> jobLocations = job.getLocations();
        List<String> preferredLocations = candidate.getPreferredLocations();
        String jobWorkMode = job.getWorkMode();

        if (jobLocations == null || jobLocations.isEmpty()) {
            return NOT_ASSESSABLE;
        }

        if (isRemoteJob(jobWorkMode)) {
            return 100.0;
        }

        if (preferredLocations == null || preferredLocations.isEmpty()) {
            return NOT_ASSESSABLE;
        }

        double maxScore = 0.0;
        for (String jobLoc : jobLocations) {
            if (jobLoc == null || jobLoc.isBlank()) continue;
            String normalizedJobLoc = normalizeToken(jobLoc);

            for (String prefLoc : preferredLocations) {
                if (prefLoc == null || prefLoc.isBlank()) continue;
                String normalizedPrefLoc = normalizeToken(prefLoc);

                if (normalizedJobLoc.contains(normalizedPrefLoc) || normalizedPrefLoc.contains(normalizedJobLoc)) {
                    return 100.0;
                }

                double partialMatch = computePartialMatch(normalizedJobLoc, normalizedPrefLoc);
                if (partialMatch > maxScore) {
                    maxScore = partialMatch;
                }
            }
        }

        return clampScore(maxScore);
    }

    private boolean isRemoteJob(String workMode) {
        if (workMode == null) return false;
        String normalized = normalizeToken(workMode);
        return normalized.contains("remote") || normalized.contains("wfh") || normalized.contains("work from home");
    }

    private double computePartialMatch(String jobLoc, String prefLoc) {
        String[] jobParts = jobLoc.split("\\s+");
        String[] prefParts = prefLoc.split("\\s+");

        int matches = 0;
        for (String jobPart : jobParts) {
            if (jobPart.length() < 3) continue;
            for (String prefPart : prefParts) {
                if (prefPart.length() < 3) continue;
                if (jobPart.equals(prefPart)) {
                    matches++;
                    break;
                }
            }
        }

        if (jobParts.length == 0) return 0.0;
        return clampScore((matches * 100.0) / jobParts.length);
    }
}
