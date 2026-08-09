package com.careerpilot.backend.modules.ai.matching.adapters.in.web;

import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.dto.ai.matching.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/ai/matching")
public class MatchingController {

    private final MatchingEngine matchingEngine;
    private final DiscoveryJobRepository jobRepository;
    private final UserRepository userRepository;

    public MatchingController(MatchingEngine matchingEngine, DiscoveryJobRepository jobRepository, UserRepository userRepository) {
        this.matchingEngine = matchingEngine;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
    }

    private UUID getUserId(Principal principal) {
        if (principal == null || principal.getName() == null) return null;
        return userRepository.findByEmail(principal.getName()).map(u -> u.getId()).orElse(null);
    }

    @PostMapping("/match")
    public ResponseEntity<MatchResultDto> matchCandidateToJob(@RequestBody MatchRequestDto request, Principal principal) {
        UUID currentUserId = request.getUserId() != null ? request.getUserId() : getUserId(principal);
        UUID candidateId = request.getCandidateId() != null ? request.getCandidateId() : currentUserId;
        if (candidateId == null) {
            candidateId = UUID.randomUUID();
        }

        Map<String, Object> jobKnowledge = request.getJobKnowledge();
        if ((jobKnowledge == null || jobKnowledge.isEmpty()) && request.getJobId() != null) {
            var jobOpt = jobRepository.findById(request.getJobId());
            if (jobOpt.isPresent()) {
                var job = jobOpt.get();
                jobKnowledge = new HashMap<>();
                jobKnowledge.put("title", job.getTitle());
                jobKnowledge.put("company", job.getCompany());
                jobKnowledge.put("locations", List.of(job.getLocation() != null ? job.getLocation() : ""));
                jobKnowledge.put("rawContent", job.getRawContent() != null ? job.getRawContent() : "");
                List<Map<String, Object>> skillsList = new ArrayList<>();
                skillsList.add(Map.of("name", job.getTitle(), "importance", "REQUIRED"));
                jobKnowledge.put("requiredSkills", skillsList);
            }
        }

        MatchResultDto result = matchingEngine.matchCandidateToJob(
                candidateId,
                request.getJobId(),
                request.getCompanyId(),
                currentUserId,
                request.getCandidateKnowledge(),
                request.getCandidateQualityMetrics(),
                request.getCandidatePreferences(),
                request.getCompanyKnowledge(),
                request.getCompanyMetadata(),
                request.getCompanyInsights(),
                jobKnowledge,
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
