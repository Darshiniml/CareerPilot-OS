package com.careerpilot.backend.modules.ai.matching.config;

import com.careerpilot.shared.dto.ai.matching.MatchWeightsDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MatchingWeightsConfig {

    private static final Map<String, Double> DEFAULT_WEIGHTS = new HashMap<>();

    static {
        DEFAULT_WEIGHTS.put("skillMatch", 0.30);
        DEFAULT_WEIGHTS.put("experienceMatch", 0.20);
        DEFAULT_WEIGHTS.put("projectMatch", 0.10);
        DEFAULT_WEIGHTS.put("technologyMatch", 0.15);
        DEFAULT_WEIGHTS.put("locationMatch", 0.05);
        DEFAULT_WEIGHTS.put("salaryMatch", 0.05);
        DEFAULT_WEIGHTS.put("cultureMatch", 0.05);
        DEFAULT_WEIGHTS.put("educationMatch", 0.05);
        DEFAULT_WEIGHTS.put("certificationMatch", 0.05);
        DEFAULT_WEIGHTS.put("responsibilityMatch", 0.05);
        DEFAULT_WEIGHTS.put("industryMatch", 0.05);
        DEFAULT_WEIGHTS.put("remotePreferenceMatch", 0.05);
        DEFAULT_WEIGHTS.put("employmentTypeMatch", 0.05);
        DEFAULT_WEIGHTS.put("careerGrowthMatch", 0.05);
        DEFAULT_WEIGHTS.put("learningOpportunityMatch", 0.05);
        DEFAULT_WEIGHTS.put("historicalSuccess", 0.0);
    }

    private Map<String, Double> customWeights = new HashMap<>();

    public MatchWeightsDto getCurrentWeights() {
        return MatchWeightsDto.builder()
                .skillMatch(getWeight("skillMatch"))
                .experienceMatch(getWeight("experienceMatch"))
                .projectMatch(getWeight("projectMatch"))
                .technologyMatch(getWeight("technologyMatch"))
                .locationMatch(getWeight("locationMatch"))
                .salaryMatch(getWeight("salaryMatch"))
                .cultureMatch(getWeight("cultureMatch"))
                .educationMatch(getWeight("educationMatch"))
                .certificationMatch(getWeight("certificationMatch"))
                .responsibilityMatch(getWeight("responsibilityMatch"))
                .industryMatch(getWeight("industryMatch"))
                .remotePreferenceMatch(getWeight("remotePreferenceMatch"))
                .employmentTypeMatch(getWeight("employmentTypeMatch"))
                .careerGrowthMatch(getWeight("careerGrowthMatch"))
                .learningOpportunityMatch(getWeight("learningOpportunityMatch"))
                .historicalSuccess(getWeight("historicalSuccess"))
                .build();
    }

    public void setCustomWeights(MatchWeightsDto weights) {
        if (weights != null) {
            if (weights.getSkillMatch() != null) customWeights.put("skillMatch", weights.getSkillMatch());
            if (weights.getExperienceMatch() != null) customWeights.put("experienceMatch", weights.getExperienceMatch());
            if (weights.getProjectMatch() != null) customWeights.put("projectMatch", weights.getProjectMatch());
            if (weights.getTechnologyMatch() != null) customWeights.put("technologyMatch", weights.getTechnologyMatch());
            if (weights.getLocationMatch() != null) customWeights.put("locationMatch", weights.getLocationMatch());
            if (weights.getSalaryMatch() != null) customWeights.put("salaryMatch", weights.getSalaryMatch());
            if (weights.getCultureMatch() != null) customWeights.put("cultureMatch", weights.getCultureMatch());
            if (weights.getEducationMatch() != null) customWeights.put("educationMatch", weights.getEducationMatch());
            if (weights.getCertificationMatch() != null) customWeights.put("certificationMatch", weights.getCertificationMatch());
            if (weights.getResponsibilityMatch() != null) customWeights.put("responsibilityMatch", weights.getResponsibilityMatch());
            if (weights.getIndustryMatch() != null) customWeights.put("industryMatch", weights.getIndustryMatch());
            if (weights.getRemotePreferenceMatch() != null) customWeights.put("remotePreferenceMatch", weights.getRemotePreferenceMatch());
            if (weights.getEmploymentTypeMatch() != null) customWeights.put("employmentTypeMatch", weights.getEmploymentTypeMatch());
            if (weights.getCareerGrowthMatch() != null) customWeights.put("careerGrowthMatch", weights.getCareerGrowthMatch());
            if (weights.getLearningOpportunityMatch() != null) customWeights.put("learningOpportunityMatch", weights.getLearningOpportunityMatch());
            if (weights.getHistoricalSuccess() != null) customWeights.put("historicalSuccess", weights.getHistoricalSuccess());
        }
    }

    public void resetToDefaults() {
        customWeights.clear();
    }

    public Double getWeight(String factorName) {
        return customWeights.getOrDefault(factorName, DEFAULT_WEIGHTS.getOrDefault(factorName, 0.0));
    }

    public Map<String, Double> getAllWeights() {
        Map<String, Double> allWeights = new HashMap<>(DEFAULT_WEIGHTS);
        allWeights.putAll(customWeights);
        return allWeights;
    }

    public void validateWeights(MatchWeightsDto weights) {
        double total = 0.0;
        if (weights.getSkillMatch() != null) total += weights.getSkillMatch();
        if (weights.getExperienceMatch() != null) total += weights.getExperienceMatch();
        if (weights.getProjectMatch() != null) total += weights.getProjectMatch();
        if (weights.getTechnologyMatch() != null) total += weights.getTechnologyMatch();
        if (weights.getLocationMatch() != null) total += weights.getLocationMatch();
        if (weights.getSalaryMatch() != null) total += weights.getSalaryMatch();
        if (weights.getCultureMatch() != null) total += weights.getCultureMatch();
        if (weights.getEducationMatch() != null) total += weights.getEducationMatch();
        if (weights.getCertificationMatch() != null) total += weights.getCertificationMatch();
        if (weights.getResponsibilityMatch() != null) total += weights.getResponsibilityMatch();
        if (weights.getIndustryMatch() != null) total += weights.getIndustryMatch();
        if (weights.getRemotePreferenceMatch() != null) total += weights.getRemotePreferenceMatch();
        if (weights.getEmploymentTypeMatch() != null) total += weights.getEmploymentTypeMatch();
        if (weights.getCareerGrowthMatch() != null) total += weights.getCareerGrowthMatch();
        if (weights.getLearningOpportunityMatch() != null) total += weights.getLearningOpportunityMatch();
        if (weights.getHistoricalSuccess() != null) total += weights.getHistoricalSuccess();

        if (total > 0 && Math.abs(total - 1.0) > 0.1) {
            throw new IllegalArgumentException("Weights must sum to approximately 1.0. Current sum: " + total);
        }
    }
}
