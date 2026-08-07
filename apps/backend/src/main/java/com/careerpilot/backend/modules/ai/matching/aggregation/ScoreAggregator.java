package com.careerpilot.backend.modules.ai.matching.aggregation;

import com.careerpilot.backend.modules.ai.matching.config.MatchingWeightsConfig;
import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.backend.modules.ai.matching.scoring.MatchScorer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ScoreAggregator {

    private final List<MatchScorer> scorers;
    private final MatchingWeightsConfig weightsConfig;

    public ScoreAggregator(List<MatchScorer> scorers, MatchingWeightsConfig weightsConfig) {
        this.scorers = scorers;
        this.weightsConfig = weightsConfig;
    }

    public AggregationResult aggregateScores(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        Map<String, Double> weights = weightsConfig.getAllWeights();
        Map<String, Double> individualScores = new java.util.HashMap<>();
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (MatchScorer scorer : scorers) {
            String factorName = scorer.getFactorName();
            double score = scorer.score(candidate, company, job);
            individualScores.put(factorName, score);

            Double weight = weights.get(factorName);
            if (weight != null && weight > 0) {
                weightedSum += score * weight;
                totalWeight += weight;
            }
        }

        double overallScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;

        return new AggregationResult(
                clampScore(overallScore),
                individualScores,
                weights
        );
    }

    private double clampScore(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }

    public record AggregationResult(
            double overallScore,
            Map<String, Double> individualScores,
            Map<String, Double> weights
    ) {}
}
