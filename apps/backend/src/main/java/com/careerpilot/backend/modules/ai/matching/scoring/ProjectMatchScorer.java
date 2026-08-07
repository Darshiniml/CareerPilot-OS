package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.jaccardSimilarity;
import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.normalizeToken;

@Component
public class ProjectMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "projectMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<String> projectTechs = candidate.getProjectTechnologies();
        List<String> projectDescs = candidate.getProjectDescriptions();
        Set<String> jobSkills = job.getRequiredSkills();
        List<String> responsibilities = job.getResponsibilities();

        if (projectTechs == null || projectTechs.isEmpty()) {
            return 0.0;
        }

        double techScore = 0.0;
        if (!jobSkills.isEmpty()) {
            Set<String> projectTechSet = Set.copyOf(projectTechs);
            techScore = jaccardSimilarity(projectTechSet, jobSkills);
        }

        double relevanceScore = 0.0;
        if (responsibilities != null && !responsibilities.isEmpty() && projectDescs != null && !projectDescs.isEmpty()) {
            relevanceScore = computeProjectRelevance(projectDescs, responsibilities);
        }

        return clampScore((techScore * 0.7) + (relevanceScore * 0.3));
    }

    private double computeProjectRelevance(List<String> projectDescs, List<String> responsibilities) {
        int matches = 0;
        int totalChecks = 0;

        for (String resp : responsibilities) {
            String normalizedResp = normalizeToken(resp);
            String[] respKeywords = normalizedResp.split("\\s+");

            for (String desc : projectDescs) {
                if (desc == null || desc.isBlank()) continue;
                String normalizedDesc = normalizeToken(desc);

                for (String keyword : respKeywords) {
                    if (keyword.length() < 3) continue;
                    totalChecks++;
                    if (normalizedDesc.contains(keyword)) {
                        matches++;
                        break;
                    }
                }
            }
        }

        if (totalChecks == 0) return 50.0;
        return clampScore((matches * 100.0) / totalChecks);
    }
}
