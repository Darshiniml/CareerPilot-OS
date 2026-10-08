package com.careerpilot.backend.modules.opportunity.services;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.matching.MatchService;

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

    private final MatchService matchService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final HistoricalSuccessSignalService historicalSignalService;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final AgentPolicyRepository policyRepository;
    private final DataCollector dataCollector;
    private final JobIntelligenceCacheRepository jobCacheRepository;
    private final CompanyIntelligenceCacheRepository companyCacheRepository;

    @Transactional(readOnly = true)
    public OpportunityDto prioritize(UUID candidateId, DiscoveryJob job) {
        // 1-3. Real match from the candidate's processed resume + the job's data (no empty-data scoring)
        Optional<MatchResultDto> matchOpt = matchService.matchIfPossible(candidateId, job.getId());
        MatchResultDto matchResult = matchOpt.orElse(null);
        boolean matchAvailable = matchResult != null;
        double matchScore = matchAvailable ? matchResult.getOverallScore() : 0.0;
        Optional<CandidateKnowledgeService.ResumeKnowledge> primaryResume = candidateKnowledgeService.primaryResume(candidateId);

        // 4. Historical Signal calculation
        UUID resumeId = primaryResume.map(CandidateKnowledgeService.ResumeKnowledge::resumeId).orElse(null);
        Integer resumeVersion = primaryResume.map(CandidateKnowledgeService.ResumeKnowledge::versionNumber).orElse(null);
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
        if (!matchAvailable) {
            priorityLevel = "UNSCORED";
        } else if (priorityScore >= 80.0) {
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
        if (!matchAvailable) {
            reasons.add("Upload a resume and let AI processing finish to score this job against your profile");
        } else if (Boolean.FALSE.equals(matchResult.getJobAnalyzed())) {
            reasons.add("Job requirements not analysed yet: skill-based factors are not assessed");
        }
        if (matchAvailable && matchScore >= 80.0) {
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
                .matchAvailable(matchAvailable)
                .notAssessedFactors(matchAvailable ? matchResult.getNotAssessedFactors() : List.of())
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
