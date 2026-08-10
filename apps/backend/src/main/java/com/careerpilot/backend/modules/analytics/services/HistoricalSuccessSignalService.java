package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoricalSuccessSignalService {

    private final AdaptiveCareerIntelligenceService careerIntelligenceService;
    private final ApplicationRecordRepository applicationRepository;

    private static final int MIN_TOTAL_APPLICATIONS = 10;

    public HistoricalSuccessSignal getHistoricalSignal(UUID candidateId, String title, String company, String location, String workMode, String source, Set<String> skills, UUID resumeId, Integer resumeVersion) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        int totalApps = apps.size();

        if (totalApps < MIN_TOTAL_APPLICATIONS) {
            return HistoricalSuccessSignal.builder()
                    .available(false)
                    .confidenceStatus("INSUFFICIENT_DATA")
                    .roleSuccessScore(0.0)
                    .skillSuccessScore(0.0)
                    .sourceSuccessScore(0.0)
                    .resumeSuccessScore(0.0)
                    .historicalSuccessScore(0.0)
                    .sampleSize(totalApps)
                    .explanation("Insufficient historical application data (minimum 10 total applications required; currently: " + totalApps + ").")
                    .build();
        }

        // Calculate role score
        double roleScore = 0.0;
        boolean roleValid = false;
        List<AdaptiveCareerIntelligenceService.RolePerformance> rolePerformances = careerIntelligenceService.getRolePerformance(candidateId);
        if (title != null) {
            String cleanTitle = title.toLowerCase().trim();
            Optional<AdaptiveCareerIntelligenceService.RolePerformance> match = rolePerformances.stream()
                    .filter(rp -> rp.getRoleName() != null && (rp.getRoleName().toLowerCase().trim().contains(cleanTitle) || cleanTitle.contains(rp.getRoleName().toLowerCase().trim())))
                    .max(Comparator.comparingInt(rp -> rp.getRoleName().length())); // Find closest match
            
            if (match.isPresent() && "SUFFICIENT".equals(match.get().getConfidenceStatus())) {
                roleScore = match.get().getInterviewRate() * 100.0;
                roleValid = true;
            }
        }

        // Calculate source score
        double sourceScore = 0.0;
        boolean sourceValid = false;
        if (source != null) {
            List<AdaptiveCareerIntelligenceService.SourcePerformance> sourcePerformances = careerIntelligenceService.getSourcePerformance(candidateId);
            String cleanSource = source.toLowerCase().trim();
            Optional<AdaptiveCareerIntelligenceService.SourcePerformance> match = sourcePerformances.stream()
                    .filter(sp -> sp.getSource() != null && sp.getSource().toLowerCase().trim().equals(cleanSource))
                    .findFirst();
            if (match.isPresent() && "SUFFICIENT".equals(match.get().getConfidenceStatus())) {
                sourceScore = match.get().getInterviewRate() * 100.0;
                sourceValid = true;
            }
        }

        // Calculate resume score
        double resumeScore = 0.0;
        boolean resumeValid = false;
        if (resumeId != null) {
            List<AdaptiveCareerIntelligenceService.ResumePerformance> resumePerformances = careerIntelligenceService.getResumeVersionPerformance(candidateId);
            int ver = resumeVersion != null ? resumeVersion : 1;
            Optional<AdaptiveCareerIntelligenceService.ResumePerformance> match = resumePerformances.stream()
                    .filter(rp -> rp.getResumeId().equals(resumeId) && rp.getResumeVersion() == ver)
                    .findFirst();
            if (match.isPresent() && "SUFFICIENT".equals(match.get().getConfidenceStatus())) {
                resumeScore = match.get().getInterviewRate() * 100.0;
                resumeValid = true;
            }
        }

        // Calculate skill score (average interview rate of matching sufficient skills)
        double skillScore = 0.0;
        boolean skillValid = false;
        if (skills != null && !skills.isEmpty()) {
            List<AdaptiveCareerIntelligenceService.SkillPerformance> skillPerformances = careerIntelligenceService.getSkillPerformance(candidateId);
            List<Double> validRates = new ArrayList<>();
            for (String jobSkill : skills) {
                String clean = jobSkill.toLowerCase().trim();
                skillPerformances.stream()
                        .filter(sp -> sp.getSkillName().equals(clean) && "SUFFICIENT".equals(sp.getConfidenceStatus()))
                        .findFirst()
                        .ifPresent(sp -> validRates.add(sp.getInterviewRate() * 100.0));
            }
            if (!validRates.isEmpty()) {
                skillScore = validRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                skillValid = true;
            }
        }

        // Compute overall score
        double sum = 0.0;
        int count = 0;
        if (roleValid) { sum += roleScore; count++; }
        if (sourceValid) { sum += sourceScore; count++; }
        if (resumeValid) { sum += resumeScore; count++; }
        if (skillValid) { sum += skillScore; count++; }

        double overallScore;
        String explanation;

        if (count > 0) {
            overallScore = sum / count;
            List<String> reasons = new ArrayList<>();
            if (roleValid) reasons.add(String.format("similar %s roles produced a %.0f%% interview rate", title, roleScore));
            if (skillValid) reasons.add(String.format("key skills required have a %.0f%% success correlation", skillScore));
            if (sourceValid) reasons.add(String.format("source %s has a %.0f%% historical response rate", source, sourceScore));
            if (resumeValid) reasons.add(String.format("selected resume version has a %.0f%% interview rate", resumeScore));
            explanation = "Prioritized because: " + String.join(", ", reasons) + ".";
        } else {
            // Fallback: overall candidate average interview rate
            AdaptiveCareerIntelligenceService.CareerOutcomeProfile outcome = careerIntelligenceService.getCareerOutcomeProfile(candidateId);
            overallScore = outcome.getInterviewRate() * 100.0;
            explanation = String.format("Overall historical interview conversion rate is %.1f%% across %d applications.", overallScore, totalApps);
        }

        return HistoricalSuccessSignal.builder()
                .available(true)
                .confidenceStatus("SUFFICIENT")
                .roleSuccessScore(roleScore)
                .skillSuccessScore(skillScore)
                .sourceSuccessScore(sourceScore)
                .resumeSuccessScore(resumeScore)
                .historicalSuccessScore(overallScore)
                .sampleSize(totalApps)
                .explanation(explanation)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalSuccessSignal {
        private boolean available;
        private String confidenceStatus;
        private double roleSuccessScore;
        private double skillSuccessScore;
        private double sourceSuccessScore;
        private double resumeSuccessScore;
        private double historicalSuccessScore;
        private int sampleSize;
        private String explanation;
    }
}
