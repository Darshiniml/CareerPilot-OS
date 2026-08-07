package com.careerpilot.backend.modules.ai.matching.ranking;

import com.careerpilot.shared.dto.ai.matching.RankedJobDto;
import com.careerpilot.shared.dto.ai.matching.RankingRequestDto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RankingEngine {

    public List<RankedJobDto> rankJobs(List<RankedJobDto> jobs, RankingRequestDto request) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }

        String strategy = request.getRankingStrategy();
        if (strategy == null || strategy.isBlank()) {
            strategy = "OVERALL_SCORE";
        }

        return switch (strategy.toUpperCase()) {
            case "OVERALL_SCORE" -> rankByOverallScore(jobs);
            case "SKILL_SCORE" -> rankBySkillScore(jobs);
            case "COMPANY_FIT" -> rankByCompanyFit(jobs);
            case "CAREER_GROWTH" -> rankByCareerGrowth(jobs);
            case "SALARY" -> rankBySalary(jobs);
            case "LEARNING_OPPORTUNITY" -> rankByLearningOpportunity(jobs);
            case "CUSTOM" -> rankByCustomWeights(jobs, request.getCustomWeights());
            default -> rankByOverallScore(jobs);
        };
    }

    private List<RankedJobDto> rankByOverallScore(List<RankedJobDto> jobs) {
        return jobs.stream()
                .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
                .collect(Collectors.toList());
    }

    private List<RankedJobDto> rankBySkillScore(List<RankedJobDto> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    double skillA = a.getIndividualScores().getOrDefault("skillMatch", 0.0);
                    double skillB = b.getIndividualScores().getOrDefault("skillMatch", 0.0);
                    return Double.compare(skillB, skillA);
                })
                .collect(Collectors.toList());
    }

    private List<RankedJobDto> rankByCompanyFit(List<RankedJobDto> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    double companyFitA = calculateCompanyFitScore(a);
                    double companyFitB = calculateCompanyFitScore(b);
                    return Double.compare(companyFitB, companyFitA);
                })
                .collect(Collectors.toList());
    }

    private List<RankedJobDto> rankByCareerGrowth(List<RankedJobDto> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    double growthA = a.getIndividualScores().getOrDefault("careerGrowthMatch", 0.0);
                    double growthB = b.getIndividualScores().getOrDefault("careerGrowthMatch", 0.0);
                    return Double.compare(growthB, growthA);
                })
                .collect(Collectors.toList());
    }

    private List<RankedJobDto> rankBySalary(List<RankedJobDto> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    double salaryA = a.getIndividualScores().getOrDefault("salaryMatch", 0.0);
                    double salaryB = b.getIndividualScores().getOrDefault("salaryMatch", 0.0);
                    return Double.compare(salaryB, salaryA);
                })
                .collect(Collectors.toList());
    }

    private List<RankedJobDto> rankByLearningOpportunity(List<RankedJobDto> jobs) {
        return jobs.stream()
                .sorted((a, b) -> {
                    double learningA = a.getIndividualScores().getOrDefault("learningOpportunityMatch", 0.0);
                    double learningB = b.getIndividualScores().getOrDefault("learningOpportunityMatch", 0.0);
                    return Double.compare(learningB, learningA);
                })
                .collect(Collectors.toList());
    }

    private List<RankedJobDto> rankByCustomWeights(List<RankedJobDto> jobs, Map<String, Double> customWeights) {
        if (customWeights == null || customWeights.isEmpty()) {
            return rankByOverallScore(jobs);
        }

        return jobs.stream()
                .sorted((a, b) -> {
                    double scoreA = calculateCustomScore(a, customWeights);
                    double scoreB = calculateCustomScore(b, customWeights);
                    return Double.compare(scoreB, scoreA);
                })
                .collect(Collectors.toList());
    }

    private double calculateCompanyFitScore(RankedJobDto job) {
        Map<String, Double> scores = job.getIndividualScores();
        double cultureFit = scores.getOrDefault("cultureMatch", 0.0);
        double growthFit = scores.getOrDefault("careerGrowthMatch", 0.0);
        double learningFit = scores.getOrDefault("learningOpportunityMatch", 0.0);
        double remoteFit = scores.getOrDefault("remotePreferenceMatch", 0.0);

        return (cultureFit * 0.3) + (growthFit * 0.3) + (learningFit * 0.2) + (remoteFit * 0.2);
    }

    private double calculateCustomScore(RankedJobDto job, Map<String, Double> weights) {
        Map<String, Double> scores = job.getIndividualScores();
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String factor = entry.getKey();
            Double weight = entry.getValue();
            if (weight != null && weight > 0) {
                double score = scores.getOrDefault(factor, 0.0);
                weightedSum += score * weight;
                totalWeight += weight;
            }
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    public List<RankedJobDto> filterByThreshold(List<RankedJobDto> jobs, double minScore) {
        return jobs.stream()
                .filter(job -> job.getOverallScore() >= minScore)
                .collect(Collectors.toList());
    }

    public List<RankedJobDto> limitResults(List<RankedJobDto> jobs, int limit) {
        if (limit <= 0) {
            return jobs;
        }
        return jobs.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
