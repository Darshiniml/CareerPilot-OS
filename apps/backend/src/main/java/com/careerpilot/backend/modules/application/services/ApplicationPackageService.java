package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.backend.modules.application.domain.ApplicationDecision;
import com.careerpilot.backend.modules.application.domain.ApplicationPackage;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionCapability;
import com.careerpilot.backend.modules.application.repositories.ApplicationPackageRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
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
public class ApplicationPackageService {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationPackageRepository packageRepository;
    private final ApplicationDecisionService decisionService;
    private final UserRepository userRepository;
    private final DiscoveryJobRepository jobRepository;
    private final MatchingEngine matchingEngine;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final SubmissionPreflightService preflightService;
    private final ObjectMapper objectMapper;

    public ApplicationPackageService(
            ApplicationRecordRepository applicationRepository,
            ApplicationPackageRepository packageRepository,
            ApplicationDecisionService decisionService,
            UserRepository userRepository,
            DiscoveryJobRepository jobRepository,
            MatchingEngine matchingEngine,
            ApplicationSubmissionRegistry submissionRegistry,
            SubmissionPreflightService preflightService,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.packageRepository = packageRepository;
        this.decisionService = decisionService;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.matchingEngine = matchingEngine;
        this.submissionRegistry = submissionRegistry;
        this.preflightService = preflightService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationPackage assemblePackage(UUID applicationId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application record not found for ID: " + applicationId));

        UUID candidateId = application.getCandidateId();
        UUID jobId = application.getJobId();

        // Ensure Decision exists
        ApplicationDecision decision = decisionService.getDecision(applicationId)
                .orElseGet(() -> decisionService.evaluateDecision(applicationId));

        User user = userRepository.findById(candidateId).orElse(null);
        DiscoveryJob job = jobRepository.findById(jobId).orElse(null);

        Map<String, Object> jobKnowledge = new HashMap<>();
        if (job != null) {
            jobKnowledge.put("title", job.getTitle());
            jobKnowledge.put("company", job.getCompany());
            jobKnowledge.put("locations", List.of(job.getLocation() != null ? job.getLocation() : ""));
            jobKnowledge.put("rawContent", job.getRawContent() != null ? job.getRawContent() : "");
        }

        MatchResultDto matchResult = matchingEngine.matchCandidateToJob(
                candidateId,
                jobId,
                application.getCompanyId(),
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

        ApplicationSubmissionCapability capability = submissionRegistry.getSubmissionCapability(application.getConnectorId());
        SubmissionPreflightService.PreflightResult preflight = preflightService.evaluatePreflight(application, candidateId);

        Map<String, Object> candidateProfile = new HashMap<>();
        if (user != null) {
            candidateProfile.put("id", user.getId());
            candidateProfile.put("firstName", user.getFirstName());
            candidateProfile.put("lastName", user.getLastName());
            candidateProfile.put("email", user.getEmail());
        }

        Map<String, Object> jobDetails = new HashMap<>();
        if (job != null) {
            jobDetails.put("id", job.getId());
            jobDetails.put("title", job.getTitle());
            jobDetails.put("company", job.getCompany());
            jobDetails.put("location", job.getLocation());
            jobDetails.put("applyUrl", job.getSourceUrl());
        }

        Map<String, Object> selectedResume = new HashMap<>();
        selectedResume.put("resumeId", decision.getRecommendedResumeId());
        selectedResume.put("title", decision.getRecommendedResumeTitle());

        ApplicationPackage pkg = packageRepository.findByApplicationId(applicationId)
                .orElse(ApplicationPackage.builder().id(UUID.randomUUID()).applicationId(applicationId).build());

        pkg.setCandidateId(candidateId);
        pkg.setJobId(jobId);
        pkg.setCandidateProfileJson(toJson(candidateProfile));
        pkg.setSelectedResumeJson(toJson(selectedResume));
        pkg.setJobDetailsJson(toJson(jobDetails));
        pkg.setMatchResultJson(toJson(matchResult));
        pkg.setCompanyIntelligenceJson(toJson(Map.of("company", job != null ? job.getCompany() : "Unknown")));
        pkg.setDecisionId(decision.getId());
        pkg.setSubmissionCapabilityJson(toJson(capability));
        pkg.setPreflightResultJson(toJson(preflight));
        // A package must never imply that CareerPilot is an external application
        // destination.  Keep this absent when the discovered job has no verified
        // source URL; preflight will then require manual resolution.
        pkg.setOfficialApplyUrl(job != null ? job.getSourceUrl() : null);
        pkg.setGeneratedAt(Instant.now());

        return packageRepository.save(pkg);
    }

    public Optional<ApplicationPackage> getPackage(UUID applicationId) {
        return packageRepository.findByApplicationId(applicationId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
