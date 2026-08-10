package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CareerLearningRecommendationService {

    private final SkillDemandAnalyzer skillDemandAnalyzer;
    private final AdaptiveCareerIntelligenceService careerIntelligenceService;
    private final ResumeRepository resumeRepository;
    private final ResumeIntelligenceCacheRepository resumeIntelligenceCacheRepository;

    public List<CareerLearningRecommendation> getCareerRecommendations(UUID candidateId) {
        // Retrieve candidate skills
        Set<String> candidateSkills = getCandidateSkills(candidateId);

        // Analyze skills demand & missing skills
        Map<String, Object> skillAnalysis = skillDemandAnalyzer.analyzeSkills(candidateSkills);
        List<String> missingSkills = (List<String>) skillAnalysis.getOrDefault("missingSkills", List.of());
        Map<String, Double> demandPercentages = (Map<String, Double>) skillAnalysis.getOrDefault("demandPercentages", Map.of());

        // Get skill outcomes from history
        List<AdaptiveCareerIntelligenceService.SkillPerformance> skillPerformances = careerIntelligenceService.getSkillPerformance(candidateId);

        List<CareerLearningRecommendation> recommendations = new ArrayList<>();

        for (String skill : missingSkills) {
            double demand = demandPercentages.getOrDefault(skill.toLowerCase(), 0.0);
            if (demand < 15.0) {
                continue; // Only recommend skills with reasonable demand
            }

            String priority = demand >= 40.0 ? "HIGH" : (demand >= 25.0 ? "MEDIUM" : "LOW");
            
            // Check if there is historical data showing strong outcomes for related skills the candidate had
            boolean relatedSkillSuccess = skillPerformances.stream()
                    .filter(sp -> "SUFFICIENT".equals(sp.getConfidenceStatus()) && sp.getInterviewRate() > 0.2)
                    .anyMatch(sp -> isRelated(sp.getSkillName(), skill));

            String reason = String.format("Learn %s to improve your matching alignment for target roles.", capitalize(skill));
            String evidence = String.format("%.1f%% of analyzed jobs in the market require %s.", demand, capitalize(skill));
            if (relatedSkillSuccess) {
                evidence += " Additionally, similar roles you applied to with related skills showed stronger historical progression.";
            }

            recommendations.add(CareerLearningRecommendation.builder()
                    .skill(skill)
                    .reason(reason)
                    .evidence(evidence)
                    .priority(priority)
                    .expectedCareerRelevance(String.format("Potential matching pool expansion of %.0f%%.", demand))
                    .build());
        }

        // Limit to top 5 recommendations by demand
        return recommendations.stream()
                .limit(5)
                .collect(Collectors.toList());
    }

    private Set<String> getCandidateSkills(UUID candidateId) {
        Set<String> candidateSkills = new HashSet<>();
        try {
            List<Resume> resumes = resumeRepository.findActiveByUserId(candidateId);
            for (Resume r : resumes) {
                if (r.getChecksumSha256() != null) {
                    Optional<ResumeIntelligenceCache> cacheOpt = resumeIntelligenceCacheRepository.findById(r.getChecksumSha256());
                    if (cacheOpt.isPresent() && cacheOpt.get().getStructuredKnowledge() != null) {
                        Map<String, Object> knowledge = cacheOpt.get().getStructuredKnowledge();
                        if (knowledge.containsKey("skills")) {
                            List<?> rawSkills = (List<?>) knowledge.get("skills");
                            for (Object item : rawSkills) {
                                if (item instanceof Map<?, ?> map) {
                                    Object skillVal = map.get("skill");
                                    if (skillVal == null) skillVal = map.get("name");
                                    if (skillVal != null) candidateSkills.add(skillVal.toString().toLowerCase().trim());
                                } else if (item != null) {
                                    candidateSkills.add(item.toString().toLowerCase().trim());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error collecting candidate skills: ", e);
        }
        return candidateSkills;
    }

    private boolean isRelated(String skillA, String skillB) {
        // Very basic relation heuristic (e.g. sharing roots like "spring" or "database")
        if (skillA == null || skillB == null) return false;
        String sa = skillA.toLowerCase();
        String sb = skillB.toLowerCase();
        if (sa.contains("spring") && sb.contains("spring")) return true;
        if (sa.contains("sql") && sb.contains("sql")) return true;
        if (sa.contains("react") && sb.contains("react")) return true;
        if (sa.contains("cloud") && sb.contains("cloud")) return true;
        if (sa.contains("aws") && sb.contains("aws")) return true;
        return false;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerLearningRecommendation {
        private String skill;
        private String reason;
        private String evidence;
        private String priority;
        private String expectedCareerRelevance;
    }
}
