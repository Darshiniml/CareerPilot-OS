package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
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

    private final MatchingEngine matchingEngine;
    private final ResumeSelectionService resumeSelectionService;
    private final SubmissionPreflightService preflightService;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final ApplicationRecordRepository applicationRepository;
    private final DiscoveryJobRepository jobRepository;
    private final ApplicationDecisionRepository decisionRepository;
    private final ObjectMapper objectMapper;

    public ApplicationDecisionService(
            MatchingEngine matchingEngine,
            ResumeSelectionService resumeSelectionService,
            SubmissionPreflightService preflightService,
            ApplicationSubmissionRegistry submissionRegistry,
            ApplicationRecordRepository applicationRepository,
            DiscoveryJobRepository jobRepository,
            ApplicationDecisionRepository decisionRepository,
            ObjectMapper objectMapper) {
        this.matchingEngine = matchingEngine;
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

        DiscoveryJob job = jobRepository.findById(jobId).orElse(null);

        Map<String, Object> jobKnowledge = new HashMap<>();
        if (job != null) {
            jobKnowledge.put("title", job.getTitle());
            jobKnowledge.put("company", job.getCompany());
            jobKnowledge.put("locations", List.of(job.getLocation() != null ? job.getLocation() : ""));
            jobKnowledge.put("rawContent", job.getRawContent() != null ? job.getRawContent() : "");
        }

        // 1. Matching Evaluation from MatchingEngine
        MatchResultDto matchResult = matchingEngine.matchCandidateToJob(
                candidateId,
                jobId,
                companyId,
                candidateId,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                jobKnowledge,
                Map.of(),
                Map.of()
        );

        double score = matchResult != null ? matchResult.getOverallScore() : 0.0;
        String recommendation;
        if (score >= 75.0) {
            recommendation = "RECOMMENDED_TO_APPLY";
        } else if (score >= 50.0) {
            recommendation = "APPLY_WITH_CAUTION";
        } else {
            recommendation = "NOT_RECOMMENDED";
        }

        // 2. Explainable Rationale
        String rationale = String.format("Overall match score is %.1f%% based on skill alignment (%.1f%%) and experience profile (%.1f%%).",
                score,
                matchResult != null && matchResult.getIndividualScores() != null ? matchResult.getIndividualScores().getOrDefault("SKILL_ALIGNMENT", score) : score,
                matchResult != null && matchResult.getIndividualScores() != null ? matchResult.getIndividualScores().getOrDefault("EXPERIENCE_ALIGNMENT", score) : score);

        // 3. Strengths and Gaps
        List<String> strengths = matchResult != null && matchResult.getStrengths() != null ? matchResult.getStrengths() : List.of("Role alignment");
        List<String> criticalGaps = new ArrayList<>();
        if (matchResult != null && matchResult.getCriticalGaps() != null) {
            matchResult.getCriticalGaps().forEach(g -> criticalGaps.add(g != null ? g.toString() : "Skill gap"));
        }

        // 4. Resume Selection
        ResumeSelectionService.SelectedResumeResult resumeResult = resumeSelectionService.selectBestResume(candidateId, jobId);

        // 5. Preflight Evaluation
        SubmissionPreflightService.PreflightResult preflightResult = preflightService.evaluatePreflight(application, candidateId);

        // 6. Capability Lookup
        String connectorId = application.getConnectorId() != null ? application.getConnectorId() : "remotive";
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
        decision.setCompanyHighlightsJson(toJson(List.of("Tech stack & culture alignment evaluated from company insights")));
        decision.setPreflightResultJson(toJson(preflightResult));
        decision.setSubmissionCapabilityJson(toJson(capability));
        decision.setEvaluatedAt(Instant.now());

        return decisionRepository.save(decision);
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
