package com.careerpilot.backend.modules.ai.matching.adapters.in.web;

import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.shared.dto.ai.matching.BulkMatchRequestDto;
import com.careerpilot.shared.dto.ai.matching.GapAnalysisRequestDto;
import com.careerpilot.shared.dto.ai.matching.GapAnalysisResultDto;
import com.careerpilot.shared.dto.ai.matching.MatchRequestDto;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.dto.ai.matching.MatchWeightsDto;
import com.careerpilot.shared.dto.ai.matching.RankingRequestDto;
import com.careerpilot.shared.dto.ai.matching.RankingResultDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationItemDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/matching")
public class MatchingController {

    private final MatchingEngine matchingEngine;

    public MatchingController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/match")
    public ResponseEntity<MatchResultDto> matchCandidateToJob(@RequestBody MatchRequestDto request) {
        MatchResultDto result = matchingEngine.matchCandidateToJob(
                request.getCandidateId(),
                request.getJobId(),
                request.getCompanyId(),
                request.getUserId(),
                request.getCandidateKnowledge(),
                request.getCandidateQualityMetrics(),
                request.getCandidatePreferences(),
                request.getCompanyKnowledge(),
                request.getCompanyMetadata(),
                request.getCompanyInsights(),
                request.getJobKnowledge(),
                request.getJobMetadata(),
                request.getJobInsights()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/match/bulk")
    public ResponseEntity<List<MatchResultDto>> bulkMatchCandidatesToJobs(@RequestBody BulkMatchRequestDto request) {
        List<MatchResultDto> results = matchingEngine.bulkMatchCandidatesToJobs(request);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<MatchResultDto> getMatchResult(@PathVariable UUID matchId) {
        // This would typically fetch from a database, but for now we'll return a placeholder
        // In a real implementation, you'd have a MatchResultRepository
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/gap-analysis")
    public ResponseEntity<GapAnalysisResultDto> performGapAnalysis(@RequestBody GapAnalysisRequestDto request) {
        GapAnalysisResultDto result = matchingEngine.performGapAnalysis(
                request.getCandidateId(),
                request.getJobId(),
                request.getCompanyId(),
                request.getCandidateKnowledge(),
                request.getCandidateQualityMetrics(),
                request.getCandidatePreferences(),
                request.getCompanyKnowledge(),
                request.getCompanyMetadata(),
                request.getCompanyInsights(),
                request.getJobKnowledge(),
                request.getJobMetadata(),
                request.getJobInsights()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/recommendations")
    public ResponseEntity<List<RecommendationItemDto>> generateRecommendations(@RequestBody RecommendationRequestDto request) {
        List<RecommendationItemDto> recommendations = matchingEngine.generateRecommendations(request);
        return ResponseEntity.ok(recommendations);
    }

    @PostMapping("/rank-jobs")
    public ResponseEntity<RankingResultDto> rankJobs(@RequestBody RankingRequestDto request) {
        RankingResultDto result = matchingEngine.rankJobs(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/weights")
    public ResponseEntity<MatchWeightsDto> getCurrentWeights() {
        MatchWeightsDto weights = matchingEngine.getCurrentWeights();
        return ResponseEntity.ok(weights);
    }

    @PostMapping("/weights")
    public ResponseEntity<Void> updateWeights(@RequestBody MatchWeightsDto weights) {
        matchingEngine.updateWeights(weights);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/weights/reset")
    public ResponseEntity<Void> resetWeights() {
        matchingEngine.resetWeights();
        return ResponseEntity.ok().build();
    }
}
