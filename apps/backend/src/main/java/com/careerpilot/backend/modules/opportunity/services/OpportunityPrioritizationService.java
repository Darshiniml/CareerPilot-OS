package com.careerpilot.backend.modules.opportunity.services;

import com.careerpilot.backend.modules.agent.domain.AgentPolicy;
import com.careerpilot.backend.modules.agent.repositories.AgentPolicyRepository;
import com.careerpilot.backend.modules.ai.company.domain.CompanyIntelligenceCache;
import com.careerpilot.backend.modules.ai.company.repositories.CompanyIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.analytics.services.DataCollector;
import com.careerpilot.backend.modules.analytics.services.HistoricalSuccessSignalService;
import com.careerpilot.backend.modules.analytics.services.HistoricalSuccessSignalService.HistoricalSuccessSignal;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.services.ApplicationSubmissionRegistry;
import com.careerpilot.backend.modules.auth.domain.UserPreference;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.dto.opportunity.OpportunityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpportunityPrioritizationService {

    private final MatchingEngine matchingEngine;
    private final HistoricalSuccessSignalService historicalSignalService;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final AgentPolicyRepository policyRepository;
    private final DataCollector dataCollector;
    private final JobIntelligenceCacheRepository jobCacheRepository;
    private final CompanyIntelligenceCacheRepository companyCacheRepository;

    @Transactional(readOnly = true)
    public OpportunityDto prioritize(UUID candidateId, DiscoveryJob job) {
        // 1. Get Candidate Data
        Map<String, Object> candidateData = dataCollector.collectCandidateData(candidateId);
        ResumeIntelligenceCache resumeCache = (ResumeIntelligenceCache) candidateData.get("resumeCache");
        UserPreference preferences = (UserPreference) candidateData.get("preferences");
        Resume defaultResume = (Resume) candidateData.get("resume");

        Map<String, Object> candidateKnowledge = resumeCache != null ? resumeCache.getStructuredKnowledge() : Map.of();
        Map<String, Object> candidateQuality = resumeCache != null ? resumeCache.getQualityMetrics() : Map.of();
        Map<String, Object> candidatePreferences = new HashMap<>();
        if (preferences != null) {
            candidatePreferences.put("preferredLocations", List.of());
            candidatePreferences.put("salaryMin", preferences.getSalaryMin());
            candidatePreferences.put("salaryMax", preferences.getSalaryMax());
            candidatePreferences.put("currencyCode", preferences.getCurrencyCode());
            candidatePreferences.put("workStyle", preferences.getWorkStyle());
            candidatePreferences.put("employmentType", preferences.getEmploymentType());
        }

        // 2. Get Job/Company Cache
        Map<String, Object> jobKnowledge = new HashMap<>();
        jobKnowledge.put("title", job.getTitle());
        jobKnowledge.put("company", job.getCompany());
        jobKnowledge.put("locations", List.of(job.getLocation() != null ? job.getLocation() : ""));
        jobKnowledge.put("rawContent", job.getRawContent() != null ? job.getRawContent() : "");

        Map<String, Object> companyKnowledge = new HashMap<>();
        Map<String, Object> companyMeta = new HashMap<>();
        Map<String, Object> companyInsights = new HashMap<>();
        if (job.getCompany() != null) {
            String cleanName = job.getCompany().toLowerCase().replaceAll("[^a-z0-9]", "");
            String companyChecksum = cleanName + "_checksum";
            Optional<CompanyIntelligenceCache> cc = companyCacheRepository.findById(companyChecksum);
            if (cc.isPresent()) {
                companyKnowledge = cc.get().getStructuredKnowledge();
                companyMeta = cc.get().getMetadata();
                companyInsights = cc.get().getInsights();
            }
        }

        // 3. Match calculation
        MatchResultDto matchResult = matchingEngine.matchCandidateToJob(
                candidateId,
                job.getId(),
                null,
                candidateId,
                candidateKnowledge,
                candidateQuality,
                candidatePreferences,
                companyKnowledge,
                companyMeta,
                companyInsights,
                jobKnowledge,
                Map.of(),
                Map.of()
        );

        double matchScore = matchResult != null ? matchResult.getOverallScore() : 0.0;

        // 4. Historical Signal calculation
        UUID resumeId = defaultResume != null ? defaultResume.getId() : null;
        Integer resumeVersion = defaultResume != null ? 1 : null;
        Set<String> skills = new HashSet<>();
        if (matchResult != null && matchResult.getMatchedSkills() != null) {
            skills.addAll(matchResult.getMatchedSkills());
        }

        HistoricalSuccessSignal histSignal = historicalSignalService.getHistoricalSignal(
                candidateId,
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getWorkMode(),
                job.getSource(),
                skills,
                resumeId,
                resumeVersion
        );

        // 5. Priority Score calculation
        double priorityScore;
        double historicalSuccessScore = 0.0;
        String confidence = "INSUFFICIENT_DATA";

        if (histSignal != null && histSignal.isAvailable()) {
            historicalSuccessScore = histSignal.getHistoricalSuccessScore();
            confidence = "SUFFICIENT";
            priorityScore = (0.70 * matchScore) + (0.30 * historicalSuccessScore);
        } else {
            priorityScore = matchScore;
        }

        String priorityLevel;
        if (priorityScore >= 80.0) {
            priorityLevel = "HIGH_PRIORITY";
        } else if (priorityScore >= 60.0) {
            priorityLevel = "MEDIUM_PRIORITY";
        } else if (priorityScore >= 40.0) {
            priorityLevel = "LOW_PRIORITY";
        } else {
            priorityLevel = "NOT_RECOMMENDED";
        }

        // 6. Application status & eligibility rules
        String applicationStatus = "NOT_APPLIED";
        String recommendedAction = "REVIEW_APPLICATION";

        Optional<ApplicationRecord> existingApp = applicationRepository.findByCandidateIdAndJobId(candidateId, job.getId());
        if (existingApp.isPresent()) {
            applicationStatus = existingApp.get().getWorkflowState().name();
            priorityLevel = "NOT_RECOMMENDED";
            recommendedAction = "NO_ACTION";
        } else {
            // Check daily limit
            AgentPolicy policy = policyRepository.findByUserId(candidateId).orElse(null);
            int maxPerDay = policy != null ? policy.getMaxApplicationsPerDay() : 5;
            long todayCount = countTodaySubmissions(candidateId);
            if (todayCount >= maxPerDay) {
                applicationStatus = "APPLICATION_BLOCKED_BY_DAILY_LIMIT";
                recommendedAction = "WAIT_FOR_DAILY_RESET";
            }
        }

        // 7. Submission mode
        String submissionMode = submissionRegistry.getSubmissionMode(job.getConnectorId()).name();
        if (applicationStatus.equals("NOT_APPLIED") && submissionMode.equals("MANUAL_REQUIRED")) {
            recommendedAction = "MANUAL_ACTION_REQUIRED";
        }

        // 8. Reasons generation
        List<String> reasons = new ArrayList<>();
        if (matchScore >= 80.0) {
            reasons.add("Strong tech stack and skills match (" + Math.round(matchScore) + "%)");
        }
        if (confidence.equals("SUFFICIENT")) {
            if (histSignal.getRoleSuccessScore() >= 50.0) {
                reasons.add("Similar roles previously produced high interview rates");
            }
            if (histSignal.getHistoricalSuccessScore() >= 50.0) {
                reasons.add("Strong historical success correlation");
            }
        } else {
            reasons.add("Historical data is insufficient for outcome prediction");
        }

        return OpportunityDto.builder()
                .jobId(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .location(job.getLocation())
                .workMode(job.getWorkMode())
                .source(job.getSource())
                .sourceUrl(job.getSourceUrl())
                .connectorId(job.getConnectorId())
                .matchScore(matchScore)
                .historicalSuccessScore(historicalSuccessScore)
                .historicalConfidence(confidence)
                .priorityScore(priorityScore)
                .priorityLevel(priorityLevel)
                .applicationStatus(applicationStatus)
                .submissionMode(submissionMode)
                .recommendedAction(recommendedAction)
                .reasons(reasons)
                .build();
    }

    private long countTodaySubmissions(UUID candidateId) {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return applicationRepository.findAll().stream()
                .filter(a -> a.getCandidateId().equals(candidateId))
                .filter(a -> a.getSubmittedAt() != null && a.getSubmittedAt().isAfter(startOfDay))
                .count();
    }
}
