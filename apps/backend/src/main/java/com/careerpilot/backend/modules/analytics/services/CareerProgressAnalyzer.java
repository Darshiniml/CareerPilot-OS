package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.profile.domain.Experience;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class CareerProgressAnalyzer {

    public double calculateCareerGrowthScore(
            List<Experience> experiences,
            Set<String> skills,
            List<ApplicationRecord> applications,
            List<InterviewSession> interviews) {

        double score = 50.0; // Base score

        // 1. Role Progression (based on experience items)
        if (experiences != null) {
            score += Math.min(15.0, experiences.size() * 3.0);
        }

        // 2. Skill Progression (based on number of skills)
        if (skills != null) {
            score += Math.min(15.0, skills.size() * 1.5);
        }

        // 3. Interview Readiness (average readiness)
        if (interviews != null && !interviews.isEmpty()) {
            double avgReadiness = interviews.stream()
                    .mapToDouble(InterviewSession::getOverallReadiness)
                    .average()
                    .orElse(0.0);
            score += avgReadiness * 10.0;
        }

        // 4. Match Score Improvement (difference between first and last application match score)
        if (applications != null && applications.size() >= 2) {
            Double latest = applications.get(0).getMatchScore();
            Double oldest = applications.get(applications.size() - 1).getMatchScore();
            if (latest != null && oldest != null) {
                double diff = latest - oldest;
                score += Math.max(-10.0, Math.min(10.0, diff));
            }
        }

        return Math.max(0.0, Math.min(100.0, score));
    }
}
