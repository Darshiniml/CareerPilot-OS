package com.careerpilot.backend.modules.ai.matching;

import com.careerpilot.backend.modules.ai.matching.aggregation.ScoreAggregator;
import com.careerpilot.backend.modules.ai.matching.explanation.ExplanationGenerator;
import com.careerpilot.backend.modules.ai.matching.gap.GapAnalysisEngine;
import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfileAnalyzer;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfileAnalyzer;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfileAnalyzer;
import com.careerpilot.backend.modules.ai.matching.recommendation.RecommendationEngine;
import com.careerpilot.backend.modules.ai.matching.ranking.RankingEngine;
import com.careerpilot.shared.dto.ai.matching.GapAnalysisResultDto;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.dto.ai.matching.RankedJobDto;
import com.careerpilot.shared.dto.ai.matching.RankingRequestDto;
import com.careerpilot.shared.dto.ai.matching.RankingResultDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationItemDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationRequestDto;
import com.careerpilot.shared.dto.ai.matching.MatchWeightsDto;
import com.careerpilot.shared.dto.ai.matching.BulkMatchRequestDto;
import com.careerpilot.shared.events.MatchCompletedEvent;
import com.careerpilot.shared.events.GapAnalysisCompletedEvent;
import com.careerpilot.shared.events.RecommendationsGeneratedEvent;
import com.careerpilot.shared.events.RankingCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MatchingEngine {

    private final CandidateProfileAnalyzer candidateProfileAnalyzer;
    private final CompanyProfileAnalyzer companyProfileAnalyzer;
    private final JobProfileAnalyzer jobProfileAnalyzer;
    private final ScoreAggregator scoreAggregator;
    private final GapAnalysisEngine gapAnalysisEngine;
    private final RecommendationEngine recommendationEngine;
    private final RankingEngine rankingEngine;
    private final ExplanationGenerator explanationGenerator;
    private final MatchingCache matchingCache;
    private final ApplicationEventPublisher eventPublisher;

    public MatchingEngine(CandidateProfileAnalyzer candidateProfileAnalyzer,
                         CompanyProfileAnalyzer companyProfileAnalyzer,
                         JobProfileAnalyzer jobProfileAnalyzer,
                         ScoreAggregator scoreAggregator,
                         GapAnalysisEngine gapAnalysisEngine,
                         RecommendationEngine recommendationEngine,
                         RankingEngine rankingEngine,
                         ExplanationGenerator explanationGenerator,
                         MatchingCache matchingCache,
                         ApplicationEventPublisher eventPublisher) {
        this.candidateProfileAnalyzer = candidateProfileAnalyzer;
        this.companyProfileAnalyzer = companyProfileAnalyzer;
        this.jobProfileAnalyzer = jobProfileAnalyzer;
        this.scoreAggregator = scoreAggregator;
        this.gapAnalysisEngine = gapAnalysisEngine;
        this.recommendationEngine = recommendationEngine;
        this.rankingEngine = rankingEngine;
        this.explanationGenerator = explanationGenerator;
        this.matchingCache = matchingCache;
        this.eventPublisher = eventPublisher;
    }

    public MatchResultDto matchCandidateToJob(UUID candidateId, UUID jobId, UUID companyId, UUID userId,
                                              Map<String, Object> candidateKnowledge,
                                              Map<String, Object> candidateQualityMetrics,
                                              Map<String, Object> candidatePreferences,
                                              Map<String, Object> companyKnowledge,
                                              Map<String, Object> companyMetadata,
                                              Map<String, Object> companyInsights,
                                              Map<String, Object> jobKnowledge,
                                              Map<String, Object> jobMetadata,
                                              Map<String, Object> jobInsights) {

        String cacheKey = MatchingCache.generateCacheKey(candidateId, jobId);
        MatchResultDto cachedResult = matchingCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        CandidateProfile candidateProfile = candidateProfileAnalyzer.analyze(
                candidateKnowledge, candidateQualityMetrics, candidatePreferences);
        candidateProfile.setCandidateId(candidateId);
        CompanyProfile companyProfile = companyProfileAnalyzer.analyze(
                companyKnowledge, companyMetadata, companyInsights);
        JobProfile jobProfile = jobProfileAnalyzer.analyze(
                jobKnowledge, jobMetadata, jobInsights);

        ScoreAggregator.AggregationResult aggregationResult = scoreAggregator.aggregateScores(
                candidateProfile, companyProfile, jobProfile);

        GapAnalysisEngine.GapAnalysisResult gapAnalysis = gapAnalysisEngine.analyzeGaps(
                candidateProfile, companyProfile, jobProfile);

        List<RecommendationItemDto> recommendations = recommendationEngine.generateRecommendations(
                candidateProfile, companyProfile, jobProfile);

        ExplanationGenerator.MatchExplanation explanation = explanationGenerator.generateExplanation(
                candidateProfile, companyProfile, jobProfile, aggregationResult);

        List<String> matchedSkills = new ArrayList<>(candidateProfile.getSkills());
        matchedSkills.retainAll(jobProfile.getRequiredSkills());
        matchedSkills.addAll(candidateProfile.getSkills().stream()
                .filter(skill -> jobProfile.getPreferredSkills().contains(skill))
                .toList());

        List<String> missingSkills = new ArrayList<>(jobProfile.getRequiredSkills());
        missingSkills.removeAll(candidateProfile.getSkills());

        double confidenceScore = calculateConfidenceScore(aggregationResult, gapAnalysis);

        MatchResultDto result = MatchResultDto.builder()
                .matchId(UUID.randomUUID())
                .candidateId(candidateId)
                .jobId(jobId)
                .companyId(companyId)
                .userId(userId)
                .overallScore(aggregationResult.overallScore())
                .individualScores(aggregationResult.individualScores())
                .strengths(explanation.strengths())
                .weaknesses(explanation.weaknesses())
                .criticalGaps(gapAnalysis.criticalGaps())
                .recommendedImprovements(gapAnalysis.recommendedImprovements())
                .optionalImprovements(gapAnalysis.optionalImprovements())
                .recommendations(recommendations)
                .confidenceScore(confidenceScore)
                .explanation(explanation.detailedExplanation())
                .matchedSkills(matchedSkills)
                .missingSkills(missingSkills)
                .notAssessedFactors(aggregationResult.notAssessedFactors())
                .build();

        matchingCache.put(cacheKey, result);

        publishMatchCompletedEvent(result);

        return result;
    }

    public List<MatchResultDto> bulkMatchCandidatesToJobs(BulkMatchRequestDto request) {
        List<MatchResultDto> results = new ArrayList<>();

        for (UUID jobId : request.getJobIds()) {
            MatchResultDto result = matchCandidateToJob(
                    request.getCandidateId(),
                    jobId,
                    request.getCompanyId(),
                    request.getUserId(),
                    request.getCandidateKnowledge(),
                    request.getCandidateQualityMetrics(),
                    request.getCandidatePreferences(),
                    request.getCompanyKnowledge(),
                    request.getCompanyMetadata(),
                    request.getCompanyInsights(),
                    request.getJobKnowledge().get(jobId.toString()),
                    request.getJobMetadata().get(jobId.toString()),
                    request.getJobInsights().get(jobId.toString())
            );
            results.add(result);
        }

        return results;
    }

    public GapAnalysisResultDto performGapAnalysis(UUID candidateId, UUID jobId, UUID companyId,
                                                   Map<String, Object> candidateKnowledge,
                                                   Map<String, Object> candidateQualityMetrics,
                                                   Map<String, Object> candidatePreferences,
                                                   Map<String, Object> companyKnowledge,
                                                   Map<String, Object> companyMetadata,
                                                   Map<String, Object> companyInsights,
                                                   Map<String, Object> jobKnowledge,
                                                   Map<String, Object> jobMetadata,
                                                   Map<String, Object> jobInsights) {

        CandidateProfile candidateProfile = candidateProfileAnalyzer.analyze(
                candidateKnowledge, candidateQualityMetrics, candidatePreferences);
        CompanyProfile companyProfile = companyProfileAnalyzer.analyze(
                companyKnowledge, companyMetadata, companyInsights);
        JobProfile jobProfile = jobProfileAnalyzer.analyze(
                jobKnowledge, jobMetadata, jobInsights);

        GapAnalysisEngine.GapAnalysisResult gapAnalysis = gapAnalysisEngine.analyzeGaps(
                candidateProfile, companyProfile, jobProfile);

        GapAnalysisResultDto result = GapAnalysisResultDto.builder()
                .candidateId(candidateId)
                .jobId(jobId)
                .companyId(companyId)
                .criticalGaps(gapAnalysis.criticalGaps())
                .recommendedImprovements(gapAnalysis.recommendedImprovements())
                .optionalImprovements(gapAnalysis.optionalImprovements())
                .build();

        publishGapAnalysisCompletedEvent(result);

        return result;
    }

    public List<RecommendationItemDto> generateRecommendations(RecommendationRequestDto request) {
        CandidateProfile candidateProfile = candidateProfileAnalyzer.analyze(
                request.getCandidateKnowledge(), request.getCandidateQualityMetrics(), request.getCandidatePreferences());
        CompanyProfile companyProfile = companyProfileAnalyzer.analyze(
                request.getCompanyKnowledge(), request.getCompanyMetadata(), request.getCompanyInsights());
        JobProfile jobProfile = jobProfileAnalyzer.analyze(
                request.getJobKnowledge(), request.getJobMetadata(), request.getJobInsights());

        List<RecommendationItemDto> recommendations = recommendationEngine.generateRecommendations(candidateProfile, companyProfile, jobProfile);

        publishRecommendationsGeneratedEvent(request.getCandidateId(), request.getJobId(), request.getCompanyId(), recommendations);

        return recommendations;
    }

    public RankingResultDto rankJobs(RankingRequestDto request) {
        List<RankedJobDto> rankedJobs = new ArrayList<>();

        for (MatchResultDto matchResult : request.getMatchResults()) {
            RankedJobDto rankedJob = RankedJobDto.builder()
                    .jobId(matchResult.getJobId())
                    .companyId(matchResult.getCompanyId())
                    .overallScore(matchResult.getOverallScore())
                    .individualScores(matchResult.getIndividualScores())
                    .strengths(matchResult.getStrengths())
                    .weaknesses(matchResult.getWeaknesses())
                    .build();
            rankedJobs.add(rankedJob);
        }

        List<RankedJobDto> ranked = rankingEngine.rankJobs(rankedJobs, request);

        if (request.getMinScore() != null && request.getMinScore() > 0) {
            ranked = rankingEngine.filterByThreshold(ranked, request.getMinScore());
        }

        if (request.getLimit() != null && request.getLimit() > 0) {
            ranked = rankingEngine.limitResults(ranked, request.getLimit());
        }

        List<UUID> rankedJobIds = ranked.stream()
                .map(RankedJobDto::getJobId)
                .toList();

        publishRankingCompletedEvent(request.getCandidateId(), rankedJobIds, request.getRankingStrategy());

        return RankingResultDto.builder()
                .candidateId(request.getCandidateId())
                .rankedJobs(ranked)
                .totalJobs(ranked.size())
                .rankingStrategy(request.getRankingStrategy())
                .build();
    }

    public void updateWeights(MatchWeightsDto weights) {
        matchingCache.getWeightsConfig().setCustomWeights(weights);
        matchingCache.clear();
    }

    public MatchWeightsDto getCurrentWeights() {
        return matchingCache.getWeightsConfig().getCurrentWeights();
    }

    public void resetWeights() {
        matchingCache.getWeightsConfig().resetToDefaults();
        matchingCache.clear();
    }

    private double calculateConfidenceScore(ScoreAggregator.AggregationResult aggregationResult,
                                           GapAnalysisEngine.GapAnalysisResult gapAnalysis) {
        double baseConfidence = aggregationResult.overallScore() / 100.0;

        double criticalGapPenalty = gapAnalysis.criticalGaps().size() * 0.1;
        double recommendedGapPenalty = gapAnalysis.recommendedImprovements().size() * 0.05;

        double confidence = baseConfidence - criticalGapPenalty - recommendedGapPenalty;
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private void publishMatchCompletedEvent(MatchResultDto matchResult) {
        MatchCompletedEvent event = MatchCompletedEvent.builder()
                .matchId(matchResult.getMatchId())
                .candidateId(matchResult.getCandidateId())
                .jobId(matchResult.getJobId())
                .companyId(matchResult.getCompanyId())
                .userId(matchResult.getUserId())
                .overallScore(matchResult.getOverallScore())
                .matchExplanation(matchResult.getExplanation())
                .build();

        eventPublisher.publishEvent(event);
    }

    private void publishGapAnalysisCompletedEvent(GapAnalysisResultDto gapResult) {
        GapAnalysisCompletedEvent event = GapAnalysisCompletedEvent.builder()
                .candidateId(gapResult.getCandidateId())
                .jobId(gapResult.getJobId())
                .companyId(gapResult.getCompanyId())
                .build();

        eventPublisher.publishEvent(event);
    }

    private void publishRecommendationsGeneratedEvent(UUID candidateId, UUID jobId, UUID companyId,
                                                      List<RecommendationItemDto> recommendations) {
        RecommendationsGeneratedEvent event = RecommendationsGeneratedEvent.builder()
                .candidateId(candidateId)
                .jobId(jobId)
                .companyId(companyId)
                .recommendationCount(recommendations.size())
                .build();

        eventPublisher.publishEvent(event);
    }

    private void publishRankingCompletedEvent(UUID candidateId, List<UUID> jobIds, String strategy) {
        RankingCompletedEvent event = RankingCompletedEvent.builder()
                .candidateId(candidateId)
                .jobIds(jobIds)
                .rankingStrategy(strategy)
                .build();

        eventPublisher.publishEvent(event);
    }
}
