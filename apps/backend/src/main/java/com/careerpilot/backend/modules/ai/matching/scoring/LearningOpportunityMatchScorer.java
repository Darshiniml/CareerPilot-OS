package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.clampScore;

@Component
public class LearningOpportunityMatchScorer implements MatchScorer {

    @Override
    public String getFactorName() {
        return "learningOpportunityMatch";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        List<String> learningOpportunities = company.getLearningOpportunities();
        List<String> benefits = company.getBenefits();
        double careerProgressionScore = candidate.getCareerProgressionScore();

        double learningScore = computeLearningScore(learningOpportunities, benefits);
        double progressionScore = careerProgressionScore;

        if (learningScore == 0.0 && progressionScore < 50.0) {
            return 40.0;
        }

        return clampScore((learningScore * 0.7) + (progressionScore * 0.3));
    }

    private double computeLearningScore(List<String> learningOpportunities, List<String> benefits) {
        double score = 0.0;

        if (learningOpportunities != null && !learningOpportunities.isEmpty()) {
            for (String opportunity : learningOpportunities) {
                if (opportunity == null || opportunity.isBlank()) continue;
                String normalized = opportunity.toLowerCase();

                if (normalized.contains("training") || normalized.contains("workshop")) {
                    score += 15.0;
                }
                if (normalized.contains("conference") || normalized.contains("meetup")) {
                    score += 10.0;
                }
                if (normalized.contains("tuition") || normalized.contains("education")) {
                    score += 20.0;
                }
                if (normalized.contains("mentorship") || normalized.contains("mentoring")) {
                    score += 15.0;
                }
                if (normalized.contains("certification") || normalized.contains("cert")) {
                    score += 15.0;
                }
                if (normalized.contains("internal mobility") || normalized.contains("role rotation")) {
                    score += 10.0;
                }
            }
        }

        if (benefits != null && !benefits.isEmpty()) {
            for (String benefit : benefits) {
                if (benefit == null || benefit.isBlank()) continue;
                String normalized = benefit.toLowerCase();

                if (normalized.contains("learning") || normalized.contains("development")) {
                    score += 10.0;
                }
                if (normalized.contains("book") || normalized.contains("course")) {
                    score += 5.0;
                }
            }
        }

        return clampScore(score);
    }
}
