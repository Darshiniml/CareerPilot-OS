package com.careerpilot.backend.modules.ai.matching;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService.ResumeKnowledge;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.shared.dto.ai.matching.GapAnalysisResultDto;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationItemDto;
import com.careerpilot.shared.dto.ai.matching.RecommendationRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Matches the authenticated candidate against a real discovered job using only server-side data:
 * the candidate's processed resume + preferences and the job's connector facts / AI analysis.
 * Deterministic scoring stays authoritative; AI is used only to explain results on request.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchingEngine matchingEngine;
    private final MatchingCache matchingCache;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final JobContextService jobContextService;
    private final AiGatewayClient gatewayClient;

    /** Everything the engine needs about one job, with a flag saying whether AI analysis exists. */
    public record JobInputs(Map<String, Object> knowledge, Map<String, Object> metadata,
                            Map<String, Object> insights, boolean analyzed) {
    }

    public MatchResultDto match(UUID userId, UUID jobId, boolean analyzeJobFirst) {
        ResumeKnowledge resume = requireResume(userId);
        if (analyzeJobFirst && jobContextService.analysis(jobId).isEmpty()) {
            jobContextService.analyze(jobId);
            matchingCache.remove(MatchingCache.generateCacheKey(userId, jobId));
        }
        JobInputs job = jobInputs(jobId);
        MatchResultDto result = matchingEngine.matchCandidateToJob(userId, jobId, null, userId,
                resume.knowledge(), resume.atsMetrics(), candidateKnowledgeService.preferences(userId),
                Map.of(), Map.of(), Map.of(),
                job.knowledge(), job.metadata(), job.insights());
        result.setJobAnalyzed(job.analyzed());
        return result;
    }

    /** Match when the candidate has a processed resume; empty otherwise (never matches empty data). */
    public Optional<MatchResultDto> matchIfPossible(UUID userId, UUID jobId) {
        if (candidateKnowledgeService.primaryResume(userId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(match(userId, jobId, false));
    }

    public GapAnalysisResultDto gapAnalysis(UUID userId, UUID jobId) {
        ResumeKnowledge resume = requireResume(userId);
        JobInputs job = jobInputs(jobId);
        return matchingEngine.performGapAnalysis(userId, jobId, null,
                resume.knowledge(), resume.atsMetrics(), candidateKnowledgeService.preferences(userId),
                Map.of(), Map.of(), Map.of(), job.knowledge(), job.metadata(), job.insights());
    }

    public List<RecommendationItemDto> recommendations(UUID userId, UUID jobId) {
        ResumeKnowledge resume = requireResume(userId);
        JobInputs job = jobInputs(jobId);
        RecommendationRequestDto request = new RecommendationRequestDto();
        request.setCandidateId(userId);
        request.setJobId(jobId);
        request.setCandidateKnowledge(resume.knowledge());
        request.setCandidateQualityMetrics(resume.atsMetrics());
        request.setCandidatePreferences(candidateKnowledgeService.preferences(userId));
        request.setCompanyKnowledge(Map.of());
        request.setCompanyMetadata(Map.of());
        request.setCompanyInsights(Map.of());
        request.setJobKnowledge(job.knowledge());
        request.setJobMetadata(job.metadata());
        request.setJobInsights(job.insights());
        return matchingEngine.generateRecommendations(request);
    }

    /** Deterministic match + an AI explanation that may not alter the scores. */
    public Map<String, Object> explain(UUID userId, UUID jobId) {
        MatchResultDto match = match(userId, jobId, false);
        Map<String, Object> matchFacts = new LinkedHashMap<>();
        matchFacts.put("overallScore", match.getOverallScore());
        matchFacts.put("componentScores", match.getIndividualScores());
        matchFacts.put("notAssessedFactors", match.getNotAssessedFactors());
        matchFacts.put("matchedSkills", match.getMatchedSkills());
        matchFacts.put("missingSkills", match.getMissingSkills());
        matchFacts.put("strengths", match.getStrengths());
        matchFacts.put("weaknesses", match.getWeaknesses());
        Map<String, Object> payload = new HashMap<>();
        payload.put("match", matchFacts);
        payload.put("job", jobContextService.jobCard(jobContextService.requireJob(jobId)));
        payload.put("candidate", Map.of("skills", candidateKnowledgeService.skillNames(userId)));
        Map<String, Object> explanation = gatewayClient.run("MATCH_EXPLANATION", payload);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("match", match);
        response.put("aiExplanation", explanation);
        return response;
    }

    /** AI skill-gap analysis grounded in the candidate's and the job's real data. */
    public Map<String, Object> aiSkillGap(UUID userId, UUID jobId) {
        requireResume(userId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("candidate", candidateKnowledgeService.candidateSummary(userId));
        payload.put("job", jobContextService.jobContext(jobId));
        return gatewayClient.run("SKILL_GAP_ANALYSIS", payload);
    }

    @SuppressWarnings("unchecked")
    public JobInputs jobInputs(UUID jobId) {
        DiscoveryJob job = jobContextService.requireJob(jobId);
        Optional<AiDocument> analysis = jobContextService.analysis(jobId);
        if (analysis.isPresent() && analysis.get().getStructuredMetadata() != null) {
            Map<String, Object> flexible = analysis.get().getFlexibleMetadata() != null
                    ? analysis.get().getFlexibleMetadata() : Map.of();
            return new JobInputs(analysis.get().getStructuredMetadata(),
                    (Map<String, Object>) flexible.getOrDefault("metadata", Map.of()),
                    (Map<String, Object>) flexible.getOrDefault("insights", Map.of()), true);
        }
        // Not analysed yet: only facts the connector provided. Requirements are NOT invented, so
        // skill-based factors are reported as not assessed until the job is analysed.
        Map<String, Object> knowledge = new HashMap<>();
        if (job.getTitle() != null) {
            knowledge.put("jobTitle", Map.of("value", job.getTitle(), "confidence", 1.0, "source", "connector"));
        }
        if (job.getLocation() != null && !job.getLocation().isBlank()) {
            knowledge.put("locations", Map.of("value", List.of(job.getLocation()), "confidence", 1.0, "source", "connector"));
        }
        if (job.getWorkMode() != null && !job.getWorkMode().isBlank()) {
            knowledge.put("workMode", Map.of("value", job.getWorkMode(), "confidence", 1.0, "source", "connector"));
        }
        if (job.getEmploymentType() != null && !job.getEmploymentType().isBlank()) {
            knowledge.put("employmentType", Map.of("value", job.getEmploymentType(), "confidence", 1.0, "source", "connector"));
        }
        if (job.getSalary() != null && !job.getSalary().isBlank()) {
            knowledge.put("salaryRange", Map.of("value", job.getSalary(), "confidence", 1.0, "source", "connector"));
        }
        return new JobInputs(knowledge, Map.of(), Map.of(), false);
    }

    private ResumeKnowledge requireResume(UUID userId) {
        return candidateKnowledgeService.primaryResume(userId).orElseThrow(() -> new IllegalStateException(
                "No processed resume yet. Upload a resume and wait for AI processing to finish before matching."));
    }
}
