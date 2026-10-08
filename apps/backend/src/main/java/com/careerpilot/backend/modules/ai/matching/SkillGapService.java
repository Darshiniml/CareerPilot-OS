package com.careerpilot.backend.modules.ai.matching;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Aggregates the candidate's matched and missing skills across recently discovered jobs whose
 * requirements were AI-analysed. Jobs without analysis are not used: their requirements are unknown.
 */
@Service
@RequiredArgsConstructor
public class SkillGapService {

    static final int SCAN_LIMIT = 60;

    private final MatchService matchService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final DiscoveryJobRepository jobRepository;

    public record SkillCount(String skill, int jobs) {
    }

    public record SkillLandscape(boolean available, String reason, int analysedJobs,
                                 List<SkillCount> missingSkills, List<SkillCount> matchedSkills) {
    }

    public SkillLandscape landscape(UUID userId) {
        if (candidateKnowledgeService.primaryResume(userId).isEmpty()) {
            return new SkillLandscape(false, "No processed resume", 0, List.of(), List.of());
        }
        Map<String, Integer> missing = new HashMap<>();
        Map<String, Integer> matched = new HashMap<>();
        int analysed = 0;
        List<DiscoveryJob> jobs = jobRepository.findAll(
                PageRequest.of(0, SCAN_LIMIT, Sort.by(Sort.Direction.DESC, "discoveredAt"))).getContent();
        for (DiscoveryJob job : jobs) {
            MatchResultDto m = matchService.match(userId, job.getId(), false);
            if (!Boolean.TRUE.equals(m.getJobAnalyzed())) {
                continue;
            }
            analysed++;
            Optional.ofNullable(m.getMissingSkills()).ifPresent(l -> l.forEach(s -> missing.merge(s, 1, Integer::sum)));
            Optional.ofNullable(m.getMatchedSkills()).ifPresent(l -> new HashSet<>(l).forEach(s -> matched.merge(s, 1, Integer::sum)));
        }
        if (analysed == 0) {
            return new SkillLandscape(false, "No analysed jobs yet: analyse some jobs to learn their real requirements",
                    0, List.of(), List.of());
        }
        return new SkillLandscape(true, null, analysed, top(missing), top(matched));
    }

    private static List<SkillCount> top(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(12)
                .map(e -> new SkillCount(e.getKey(), e.getValue()))
                .toList();
    }
}
