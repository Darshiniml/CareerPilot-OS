package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.ai.matching.MatchService;
import com.careerpilot.backend.modules.application.domain.ApplicationDecision;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionCapability;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationDecisionRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class ApplicationDecisionService {

    private final MatchService matchService;
    private final ResumeSelectionService resumeSelectionService;
    private final SubmissionPreflightService preflightService;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final ApplicationRecordRepository applicationRepository;
    private final DiscoveryJobRepository jobRepository;
    private final ApplicationDecisionRepository decisionRepository;
    private final ObjectMapper objectMapper;

    public ApplicationDecisionService(
            MatchService matchService,
            ResumeSelectionService resumeSelectionService,
            SubmissionPreflightService preflightService,
            ApplicationSubmissionRegistry submissionRegistry,
            ApplicationRecordRepository applicationRepository,
            DiscoveryJobRepository jobRepository,
            ApplicationDecisionRepository decisionRepository,
            ObjectMapper objectMapper) {
        this.matchService = matchService;
        this.resumeSelectionService = resumeSelectionService;
        this.preflightService = preflightService;
        this.submissionRegistry = submissionRegistry;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.decisionRepository = decisionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationDecision evaluateDecision(UUID applicationId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application record not found for ID: " + applicationId));

        UUID candidateId = application.getCandidateId();
        UUID jobId = application.getJobId();
        UUID companyId = application.getCompanyId();

        // 1. Matching evaluation from the candidate's real processed resume (never empty data)
        MatchResultDto matchResult = matchService.matchIfPossible(candidateId, jobId).orElse(null);

        String recommendation;
        String rationale;
        List<String> strengths = new ArrayList<>();
        List<String> criticalGaps = new ArrayList<>();
        if (matchResult == null) {
            recommendation = "INSUFFICIENT_DATA";
            rationale = "No processed resume is available yet, so this job cannot be evaluated. "
                    + "Upload a resume and wait for AI processing to finish.";
        } else {
            double score = matchResult.getOverallScore();
            recommendation = score >= 75.0 ? "RECOMMENDED_TO_APPLY" : score >= 50.0 ? "APPLY_WITH_CAUTION" : "NOT_RECOMMENDED";
            Map<String, Double> factors = matchResult.getIndividualScores() != null ? matchResult.getIndividualScores() : Map.of();
            rationale = String.format("Overall match score is %.1f%%. Skills: %s. Experience: %s.%s",
                    score, factorText(factors, "skillMatch"), factorText(factors, "experienceMatch"),
                    Boolean.FALSE.equals(matchResult.getJobAnalyzed())
                            ? " The job has not been AI-analysed yet, so requirement-based factors are not assessed." : "");
            if (matchResult.getStrengths() != null) {
                strengths.addAll(matchResult.getStrengths());
            }
            if (matchResult.getCriticalGaps() != null) {
                matchResult.getCriticalGaps().forEach(g -> { if (g != null) criticalGaps.add(g.toString()); });
            }
        }

        // 4. Resume Selection
        ResumeSelectionService.SelectedResumeResult resumeResult = resumeSelectionService.selectBestResume(candidateId, jobId);

        // 5. Preflight Evaluation
        SubmissionPreflightService.PreflightResult preflightResult = preflightService.evaluatePreflight(application, candidateId);

        // 6. Capability Lookup
        String connectorId = application.getConnectorId();
        ApplicationSubmissionCapability capability = submissionRegistry.getSubmissionCapability(connectorId);

        // 7. Persist Decision Record
        ApplicationDecision decision = decisionRepository.findByApplicationId(applicationId)
                .orElse(ApplicationDecision.builder().id(UUID.randomUUID()).applicationId(applicationId).build());

        decision.setCandidateId(candidateId);
        decision.setJobId(jobId);
        decision.setRecommendation(recommendation);
        decision.setDecisionRationale(rationale);
        decision.setStrengthsJson(toJson(strengths));
        decision.setCriticalGapsJson(toJson(criticalGaps));
        decision.setRecommendedResumeId(resumeResult.getResumeId());
        decision.setRecommendedResumeTitle(resumeResult.getTitle());
        decision.setCompanyHighlightsJson(toJson(List.of()));
        decision.setPreflightResultJson(toJson(preflightResult));
        decision.setSubmissionCapabilityJson(toJson(capability));
        decision.setEvaluatedAt(Instant.now());

        return decisionRepository.save(decision);
    }

    private static String factorText(Map<String, Double> factors, String name) {
        Double value = factors.get(name);
        return value == null ? "not assessed (missing data)" : String.format("%.0f%%", value);
    }

    public Optional<ApplicationDecision> getDecision(UUID applicationId) {
        return decisionRepository.findByApplicationId(applicationId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
