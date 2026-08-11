package com.careerpilot.backend.modules.analytics.services;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdaptiveCareerInsightsService {

    private final AdaptiveCareerIntelligenceService careerIntelligenceService;

    public List<CareerInsight> generateAdaptiveInsights(UUID candidateId) {
        AdaptiveCareerIntelligenceService.CareerOutcomeProfile profile = careerIntelligenceService.getCareerOutcomeProfile(candidateId);
        if (profile.getTotalApplications() == 0) {
            return Collections.emptyList();
        }

        List<CareerInsight> insights = new ArrayList<>();

        // 1. Role Insight
        List<AdaptiveCareerIntelligenceService.RolePerformance> roles = careerIntelligenceService.getRolePerformance(candidateId);
        Optional<AdaptiveCareerIntelligenceService.RolePerformance> bestRole = roles.stream()
                .filter(r -> "SUFFICIENT".equals(r.getConfidenceStatus()))
                .max(Comparator.comparingDouble(AdaptiveCareerIntelligenceService.RolePerformance::getInterviewRate));

        if (bestRole.isPresent()) {
            AdaptiveCareerIntelligenceService.RolePerformance rp = bestRole.get();
            insights.add(CareerInsight.builder()
                    .type("ROLE_PERFORMANCE")
                    .title("Top Performing Role Category")
                    .explanation(String.format("%s roles are associated with your strongest historical interview rate.", rp.getRoleName()))
                    .evidence(String.format("You secured %d interviews from %d applications for %s roles (%.0f%% success rate).",
                            rp.getInterviews(), rp.getApplications(), rp.getRoleName(), rp.getInterviewRate() * 100.0))
                    .confidenceStatus("SUFFICIENT")
                    .generatedAt(Instant.now())
                    .build());
        }

        // 2. Resume Insight
        List<AdaptiveCareerIntelligenceService.ResumePerformance> resumes = careerIntelligenceService.getResumeVersionPerformance(candidateId);
        boolean hasInsufficientResume = resumes.stream().anyMatch(r -> "INSUFFICIENT_DATA".equals(r.getConfidenceStatus()));
        if (hasInsufficientResume) {
            insights.add(CareerInsight.builder()
                    .type("RESUME_PERFORMANCE")
                    .title("Resume Version Comparison Pending")
                    .explanation("Some of your resume versions have insufficient historical data for a reliable comparison.")
                    .evidence("At least 5 applications per resume version are required to evaluate relative performance.")
                    .confidenceStatus("INSUFFICIENT_DATA")
                    .generatedAt(Instant.now())
                    .build());
        } else if (resumes.size() >= 2) {
            resumes.stream()
                    .max(Comparator.comparingDouble(AdaptiveCareerIntelligenceService.ResumePerformance::getInterviewRate))
                    .ifPresent(bestResume -> {
                        insights.add(CareerInsight.builder()
                                .type("RESUME_PERFORMANCE")
                                .title("Top Performing Resume Version")
                                .explanation(String.format("Applications using '%s' are associated with a stronger interview conversion rate.", bestResume.getTitle()))
                                .evidence(String.format("Interview rate of %.0f%% across %d applications.", bestResume.getInterviewRate() * 100.0, bestResume.getApplications()))
                                .confidenceStatus("SUFFICIENT")
                                .generatedAt(Instant.now())
                                .build());
                    });
        }

        // 3. Remote/Work Mode Insight
        List<AdaptiveCareerIntelligenceService.RemoteTypePerformance> modes = careerIntelligenceService.getRemoteTypePerformance(candidateId);
        modes.stream()
                .filter(m -> "SUFFICIENT".equals(m.getConfidenceStatus()) && m.getApplications() > 0)
                .max(Comparator.comparingDouble(AdaptiveCareerIntelligenceService.RemoteTypePerformance::getInterviewRate))
                .ifPresent(bestMode -> {
                    insights.add(CareerInsight.builder()
                            .type("WORK_MODE_PERFORMANCE")
                            .title("Preferred Work Mode Outcomes")
                            .explanation(String.format("%s applications are associated with your strongest conversion to interviews.", bestMode.getWorkMode()))
                            .evidence(String.format("You received %d interviews from %d applications for %s roles.",
                                    bestMode.getInterviews(), bestMode.getApplications(), bestMode.getWorkMode()))
                            .confidenceStatus("SUFFICIENT")
                            .generatedAt(Instant.now())
                            .build());
                });

        // 4. Skills Outcome Insight
        List<AdaptiveCareerIntelligenceService.SkillPerformance> skills = careerIntelligenceService.getSkillPerformance(candidateId);
        Optional<AdaptiveCareerIntelligenceService.SkillPerformance> topSkill = skills.stream()
                .filter(s -> "SUFFICIENT".equals(s.getConfidenceStatus()))
                .max(Comparator.comparingDouble(AdaptiveCareerIntelligenceService.SkillPerformance::getInterviewRate));

        if (topSkill.isPresent()) {
            AdaptiveCareerIntelligenceService.SkillPerformance sp = topSkill.get();
            insights.add(CareerInsight.builder()
                    .type("SKILL_OUTCOME")
                    .title("Key Skill Success Correlation")
                    .explanation(String.format("The skill '%s' appears frequently in opportunities that progressed to interviews.", sp.getSkillName()))
                    .evidence(String.format("Applications listing '%s' have a %.0f%% interview rate across %d submissions.",
                            sp.getSkillName(), sp.getInterviewRate() * 100.0, sp.getApplications()))
                    .confidenceStatus("SUFFICIENT")
                    .generatedAt(Instant.now())
                    .build());
        }

        // Zero-data/Insufficient fallback
        if (insights.isEmpty()) {
            insights.add(CareerInsight.builder()
                    .type("GENERAL")
                    .title("Insufficient Career History")
                    .explanation("More application outcomes are needed to generate tailored adaptive career insights.")
                    .evidence("To unlock performance insights, please apply and record outcomes (interviews or offers) for at least 5 applications in each dimension.")
                    .confidenceStatus("INSUFFICIENT_DATA")
                    .generatedAt(Instant.now())
                    .build());
        }

        return insights;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerInsight {
        private String type;
        private String title;
        private String explanation;
        private String evidence;
        private String confidenceStatus;
        private Instant generatedAt;
    }
}
