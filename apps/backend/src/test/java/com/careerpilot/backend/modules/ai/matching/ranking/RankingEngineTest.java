package com.careerpilot.backend.modules.ai.matching.ranking;

import com.careerpilot.shared.dto.ai.matching.RankedJobDto;
import com.careerpilot.shared.dto.ai.matching.RankingRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingEngineTest {

    private RankingEngine rankingEngine;

    @BeforeEach
    void setUp() {
        rankingEngine = new RankingEngine();
    }

    @Test
    void testRankByOverallScore() {
        List<RankedJobDto> jobs = createTestJobs();

        RankingRequestDto request = RankingRequestDto.builder()
                .candidateId(UUID.randomUUID())
                .matchResults(createMatchResults())
                .rankingStrategy("OVERALL_SCORE")
                .build();

        List<RankedJobDto> ranked = rankingEngine.rankJobs(jobs, request);

        assertEquals(3, ranked.size());
        assertTrue(ranked.get(0).getOverallScore() >= ranked.get(1).getOverallScore());
        assertTrue(ranked.get(1).getOverallScore() >= ranked.get(2).getOverallScore());
    }

    @Test
    void testRankBySkillScore() {
        List<RankedJobDto> jobs = createTestJobs();

        RankingRequestDto request = RankingRequestDto.builder()
                .candidateId(UUID.randomUUID())
                .matchResults(createMatchResults())
                .rankingStrategy("SKILL_SCORE")
                .build();

        List<RankedJobDto> ranked = rankingEngine.rankJobs(jobs, request);

        assertEquals(3, ranked.size());
        double firstSkillScore = ranked.get(0).getIndividualScores().get("skillMatch");
        double secondSkillScore = ranked.get(1).getIndividualScores().get("skillMatch");
        assertTrue(firstSkillScore >= secondSkillScore);
    }

    @Test
    void testFilterByThreshold() {
        List<RankedJobDto> jobs = createTestJobs();

        List<RankedJobDto> filtered = rankingEngine.filterByThreshold(jobs, 80.0);

        assertTrue(filtered.size() <= jobs.size());
        for (RankedJobDto job : filtered) {
            assertTrue(job.getOverallScore() >= 80.0);
        }
    }

    @Test
    void testLimitResults() {
        List<RankedJobDto> jobs = createTestJobs();

        List<RankedJobDto> limited = rankingEngine.limitResults(jobs, 2);

        assertEquals(2, limited.size());
    }

    @Test
    void testRankByCompanyFit() {
        List<RankedJobDto> jobs = createTestJobs();

        RankingRequestDto request = RankingRequestDto.builder()
                .candidateId(UUID.randomUUID())
                .matchResults(createMatchResults())
                .rankingStrategy("COMPANY_FIT")
                .build();

        List<RankedJobDto> ranked = rankingEngine.rankJobs(jobs, request);

        assertEquals(3, ranked.size());
    }

    @Test
    void testRankByCareerGrowth() {
        List<RankedJobDto> jobs = createTestJobs();

        RankingRequestDto request = RankingRequestDto.builder()
                .candidateId(UUID.randomUUID())
                .matchResults(createMatchResults())
                .rankingStrategy("CAREER_GROWTH")
                .build();

        List<RankedJobDto> ranked = rankingEngine.rankJobs(jobs, request);

        assertEquals(3, ranked.size());
    }

    @Test
    void testEmptyJobList() {
        List<RankedJobDto> jobs = List.of();

        RankingRequestDto request = RankingRequestDto.builder()
                .candidateId(UUID.randomUUID())
                .matchResults(List.of())
                .rankingStrategy("OVERALL_SCORE")
                .build();

        List<RankedJobDto> ranked = rankingEngine.rankJobs(jobs, request);

        assertEquals(0, ranked.size());
    }

    private List<RankedJobDto> createTestJobs() {
        return List.of(
                createRankedJob(90.0, 85.0, 80.0),
                createRankedJob(75.0, 90.0, 70.0),
                createRankedJob(85.0, 70.0, 90.0)
        );
    }

    private List<com.careerpilot.shared.dto.ai.matching.MatchResultDto> createMatchResults() {
        return List.of(
                createMatchResult(90.0, 85.0, 80.0),
                createMatchResult(75.0, 90.0, 70.0),
                createMatchResult(85.0, 70.0, 90.0)
        );
    }

    private RankedJobDto createRankedJob(double overall, double skill, double growth) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("skillMatch", skill);
        scores.put("careerGrowthMatch", growth);
        scores.put("cultureMatch", 75.0);
        scores.put("learningOpportunityMatch", 80.0);
        scores.put("remotePreferenceMatch", 90.0);

        return RankedJobDto.builder()
                .jobId(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .overallScore(overall)
                .individualScores(scores)
                .strengths(List.of("Strong skill match"))
                .weaknesses(List.of("Limited experience"))
                .build();
    }

    private com.careerpilot.shared.dto.ai.matching.MatchResultDto createMatchResult(double overall, double skill, double growth) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("skillMatch", skill);
        scores.put("careerGrowthMatch", growth);
        scores.put("cultureMatch", 75.0);
        scores.put("learningOpportunityMatch", 80.0);
        scores.put("remotePreferenceMatch", 90.0);

        return com.careerpilot.shared.dto.ai.matching.MatchResultDto.builder()
                .matchId(UUID.randomUUID())
                .candidateId(UUID.randomUUID())
                .jobId(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .overallScore(overall)
                .individualScores(scores)
                .strengths(List.of("Strong skill match"))
                .weaknesses(List.of("Limited experience"))
                .build();
    }
}
