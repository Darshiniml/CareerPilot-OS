package com.careerpilot.backend.modules.ai.matching.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.ai.matching.MatchService;
import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.shared.dto.ai.matching.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Job matching for the authenticated candidate. The candidate is always the caller: candidate ids,
 * user ids and "knowledge" supplied in request bodies are ignored. Candidate data comes from the
 * caller's processed resume and preferences; job data from the discovered job and its AI analysis.
 */
@RestController
@RequestMapping("/api/v1/ai/matching")
public class MatchingController {

    private static final int MAX_BULK = 25;

    private final MatchService matchService;
    private final MatchingEngine matchingEngine;
    private final CurrentUser currentUser;

    public MatchingController(MatchService matchService, MatchingEngine matchingEngine, CurrentUser currentUser) {
        this.matchService = matchService;
        this.matchingEngine = matchingEngine;
        this.currentUser = currentUser;
    }

    @PostMapping("/match")
    public ResponseEntity<MatchResultDto> match(@RequestBody Map<String, Object> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        boolean analyze = Boolean.TRUE.equals(body.get("analyzeJob"));
        return ResponseEntity.ok(matchService.match(userId, jobId(body), analyze));
    }

    @PostMapping("/match/explain")
    public ResponseEntity<Map<String, Object>> explain(@RequestBody Map<String, Object> body, Principal principal) {
        return ResponseEntity.ok(matchService.explain(currentUser.requireId(principal), jobId(body)));
    }

    @PostMapping("/match/bulk")
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<MatchResultDto>> bulk(@RequestBody Map<String, Object> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        List<UUID> jobIds = ids(body.get("jobIds"));
        List<MatchResultDto> results = new ArrayList<>();
        for (UUID jobId : jobIds) {
            results.add(matchService.match(userId, jobId, false));
        }
        return ResponseEntity.ok(results);
    }

    @PostMapping("/gap-analysis")
    public ResponseEntity<Object> gapAnalysis(@RequestBody Map<String, Object> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        UUID jobId = jobId(body);
        if (Boolean.TRUE.equals(body.get("ai"))) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deterministic", matchService.gapAnalysis(userId, jobId));
            response.put("ai", matchService.aiSkillGap(userId, jobId));
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(matchService.gapAnalysis(userId, jobId));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<List<RecommendationItemDto>> recommendations(@RequestBody Map<String, Object> body, Principal principal) {
        return ResponseEntity.ok(matchService.recommendations(currentUser.requireId(principal), jobId(body)));
    }

    @PostMapping("/rank-jobs")
    public ResponseEntity<RankingResultDto> rankJobs(@RequestBody Map<String, Object> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        List<MatchResultDto> matches = new ArrayList<>();
        for (UUID jobId : ids(body.get("jobIds"))) {
            matches.add(matchService.match(userId, jobId, false));
        }
        return ResponseEntity.ok(matchingEngine.rankJobs(RankingRequestDto.builder()
                .candidateId(userId).userId(userId).matchResults(matches).build()));
    }

    @GetMapping("/weights")
    public ResponseEntity<MatchWeightsDto> getCurrentWeights() {
        return ResponseEntity.ok(matchingEngine.getCurrentWeights());
    }

    /** Scoring weights are global configuration: administrators only. */
    @PostMapping("/weights")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateWeights(@RequestBody MatchWeightsDto weights) {
        matchingEngine.updateWeights(weights);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/weights/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetWeights() {
        matchingEngine.resetWeights();
        return ResponseEntity.ok().build();
    }

    private static UUID jobId(Map<String, Object> body) {
        Object raw = body.get("jobId");
        if (raw == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("jobId must be a valid id");
        }
    }

    private static List<UUID> ids(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("jobIds must be a non-empty list");
        }
        if (list.size() > MAX_BULK) {
            throw new IllegalArgumentException("At most " + MAX_BULK + " jobs per request");
        }
        List<UUID> ids = new ArrayList<>();
        for (Object o : list) {
            ids.add(UUID.fromString(String.valueOf(o)));
        }
        return ids;
    }
}
