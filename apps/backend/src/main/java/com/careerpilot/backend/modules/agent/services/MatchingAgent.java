package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.ai.company.domain.CompanyIntelligenceCache;
import com.careerpilot.backend.modules.ai.company.repositories.CompanyIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.matching.MatchingEngine;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.analytics.services.DataCollector;
import com.careerpilot.backend.modules.auth.domain.UserPreference;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.domain.JobSearchCriteria;
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.dto.opportunity.OpportunityDto;
import com.careerpilot.shared.events.JobsMatchedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MatchingAgent implements CareerAgent {

    private final JobDiscoveryService jobDiscoveryService;
    private final MatchingEngine matchingEngine;
    private final DataCollector dataCollector;
    private final JobIntelligenceCacheRepository jobCacheRepository;
    private final CompanyIntelligenceCacheRepository companyCacheRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JobDiscoveryAgent jobDiscoveryAgent;
    private final OpportunityPrioritizationService prioritizationService;

    public MatchingAgent(JobDiscoveryService jobDiscoveryService,
                         MatchingEngine matchingEngine,
                         DataCollector dataCollector,
                         JobIntelligenceCacheRepository jobCacheRepository,
                         CompanyIntelligenceCacheRepository companyCacheRepository,
                         ApplicationEventPublisher eventPublisher,
                         JobDiscoveryAgent jobDiscoveryAgent,
                         OpportunityPrioritizationService prioritizationService) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.matchingEngine = matchingEngine;
        this.dataCollector = dataCollector;
        this.jobCacheRepository = jobCacheRepository;
        this.companyCacheRepository = companyCacheRepository;
        this.eventPublisher = eventPublisher;
        this.jobDiscoveryAgent = jobDiscoveryAgent;
        this.prioritizationService = prioritizationService;
    }

    @Override
    public String getAgentId() {
        return "matching-agent";
    }

    @Override
    public String getName() {
        return "AI Candidate ↔ Opportunity Match Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("OPPORTUNITY_SCORING", "EXPLANATION_GENERATION", "GAP_ANALYSIS");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("JOB_MATCHING", "FILTER_PERSONALIZED_JOBS", "MATCH_CANDIDATE", "CALCULATE_HISTORICAL_SIGNAL", "PRIORITIZE_OPPORTUNITIES");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        String type = task.getTaskType();

        try {
            if ("FILTER_PERSONALIZED_JOBS".equals(type)) {
                JobSearchCriteria criteria = jobDiscoveryAgent.deriveCriteriaForUser(userId);
                List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
                List<DiscoveryJob> filtered = jobs.stream()
                        .filter(job -> matchesCriteria(job, criteria))
                        .toList();

                Map<String, Object> output = new HashMap<>();
                output.put("filteredJobIds", filtered.stream().map(j -> j.getId().toString()).collect(Collectors.toList()));
                output.put("count", filtered.size());

                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Filtered " + filtered.size() + " jobs matching personalized search criteria.")
                        .outputData(output)
                        .build();

            } else if ("MATCH_CANDIDATE".equals(type)) {
                // Perform matching on filtered jobs
                JobSearchCriteria criteria = jobDiscoveryAgent.deriveCriteriaForUser(userId);
                List<DiscoveryJob> jobs = jobDiscoveryService.jobs().stream()
                        .filter(job -> matchesCriteria(job, criteria))
                        .toList();

                Map<String, Object> candidateData = dataCollector.collectCandidateData(userId);
                ResumeIntelligenceCache resumeCache = (ResumeIntelligenceCache) candidateData.get("resumeCache");
                UserPreference preferences = (UserPreference) candidateData.get("preferences");

                Map<String, Object> candidateKnowledge = resumeCache != null ? resumeCache.getStructuredKnowledge() : Map.of();
                Map<String, Object> candidateQuality = resumeCache != null ? resumeCache.getQualityMetrics() : Map.of();
                Map<String, Object> candidatePreferences = new HashMap<>();
                if (preferences != null) {
                    candidatePreferences.put("preferredRoles", List.of(preferences.getEmploymentType() != null ? preferences.getEmploymentType() : "FULL_TIME"));
                    candidatePreferences.put("preferredLocations", List.of("Remote"));
                    candidatePreferences.put("salaryExpectation", preferences.getSalaryMin() != null ? preferences.getSalaryMin() : 0);
                    candidatePreferences.put("workMode", preferences.getWorkStyle() != null ? preferences.getWorkStyle() : "REMOTE");
                }

                Map<String, Object> matchScores = new HashMap<>();
                for (DiscoveryJob job : jobs) {
                    Map<String, Object> jobKnowledge = new HashMap<>();
                    jobKnowledge.put("title", job.getTitle());
                    jobKnowledge.put("company", job.getCompany());
                    jobKnowledge.put("locations", List.of(job.getLocation() != null ? job.getLocation() : ""));
                    jobKnowledge.put("rawContent", job.getRawContent() != null ? job.getRawContent() : "");

                    MatchResultDto matchResult = matchingEngine.matchCandidateToJob(
                            userId,
                            job.getId(),
                            UUID.randomUUID(),
                            userId,
                            candidateKnowledge,
                            candidateQuality,
                            candidatePreferences,
                            Map.of(),
                            Map.of(),
                            Map.of(),
                            jobKnowledge,
                            Map.of(),
                            Map.of()
                    );
                    if (matchResult != null) {
                        matchScores.put(job.getId().toString(), matchResult.getOverallScore());
                    }
                }

                Map<String, Object> output = new HashMap<>();
                output.put("matchScores", matchScores);

                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Calculated match scores for " + jobs.size() + " jobs.")
                        .outputData(output)
                        .build();

            } else if ("CALCULATE_HISTORICAL_SIGNAL".equals(type) || "PRIORITIZE_OPPORTUNITIES".equals(type)) {
                // Calculate opportunities and priority queue
                JobSearchCriteria criteria = jobDiscoveryAgent.deriveCriteriaForUser(userId);
                List<DiscoveryJob> jobs = jobDiscoveryService.jobs().stream()
                        .filter(job -> matchesCriteria(job, criteria))
                        .toList();

                List<Map<String, Object>> list = new ArrayList<>();
                for (DiscoveryJob job : jobs) {
                    OpportunityDto opp = prioritizationService.prioritize(userId, job);
                    Map<String, Object> item = new HashMap<>();
                    item.put("jobId", opp.getJobId().toString());
                    item.put("priorityScore", opp.getPriorityScore());
                    item.put("priorityLevel", opp.getPriorityLevel());
                    item.put("historicalConfidence", opp.getHistoricalConfidence());
                    item.put("historicalSuccessScore", opp.getHistoricalSuccessScore());
                    list.add(item);
                }

                Map<String, Object> output = new HashMap<>();
                output.put("opportunities", list);

                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Calculated priority metrics for " + jobs.size() + " opportunities.")
                        .outputData(output)
                        .build();

            } else {
                // Default original behavior for JOB_MATCHING
                AgentPolicy policy = context.getPolicy();
                double minScore = policy != null ? policy.getMinimumMatchScore() : 70.0;

                Map<String, Object> candidateData = dataCollector.collectCandidateData(userId);
                ResumeIntelligenceCache resumeCache = (ResumeIntelligenceCache) candidateData.get("resumeCache");
                UserPreference preferences = (UserPreference) candidateData.get("preferences");

                if (resumeCache == null) {
                    return AgentResult.builder()
                            .status(AgentResult.Status.FAILED)
                            .message("Candidate resume has not been analyzed yet. Please complete Resume Analysis first.")
                            .build();
                }

                Map<String, Object> candidateKnowledge = resumeCache.getStructuredKnowledge();
                Map<String, Object> candidateQuality = resumeCache.getQualityMetrics();
                Map<String, Object> candidatePreferences = new HashMap<>();
                if (preferences != null) {
                    candidatePreferences.put("preferredRoles", List.of(preferences.getEmploymentType() != null ? preferences.getEmploymentType() : "FULL_TIME"));
                    candidatePreferences.put("preferredLocations", List.of("Remote"));
                    candidatePreferences.put("salaryExpectation", preferences.getSalaryMin() != null ? preferences.getSalaryMin() : 0);
                    candidatePreferences.put("workMode", preferences.getWorkStyle() != null ? preferences.getWorkStyle() : "REMOTE");
                }

                List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
                List<Map<String, Object>> matchedJobsList = new ArrayList<>();
                List<UUID> matchedJobIds = new ArrayList<>();

                for (DiscoveryJob job : jobs) {
                    String jobChecksum = job.getContentHash();
                    if (jobChecksum == null || jobChecksum.isBlank()) {
                        continue;
                    }

                    Optional<JobIntelligenceCache> jobCacheOpt = jobCacheRepository.findById(jobChecksum);
                    if (jobCacheOpt.isEmpty()) {
                        List<JobIntelligenceCache> caches = jobCacheRepository.findAll();
                        if (!caches.isEmpty()) {
                            jobCacheOpt = Optional.of(caches.get(0));
                        }
                    }
                    if (jobCacheOpt.isEmpty()) {
                        continue;
                    }
                    JobIntelligenceCache jobCache = jobCacheOpt.get();

                    Map<String, Object> companyKnowledge = new HashMap<>();
                    Map<String, Object> companyMeta = new HashMap<>();
                    Map<String, Object> companyInsights = new HashMap<>();

                    if (job.getCompany() != null) {
                        String cleanName = job.getCompany().toLowerCase().replaceAll("[^a-z0-9]", "");
                        String companyChecksum = cleanName + "_checksum";
                        Optional<CompanyIntelligenceCache> companyCacheOpt = companyCacheRepository.findById(companyChecksum);
                        if (companyCacheOpt.isPresent()) {
                            companyKnowledge = companyCacheOpt.get().getStructuredKnowledge();
                            companyMeta = companyCacheOpt.get().getMetadata();
                            companyInsights = companyCacheOpt.get().getInsights();
                        }
                    }

                    MatchResultDto matchResult = matchingEngine.matchCandidateToJob(
                            userId,
                            job.getId(),
                            UUID.randomUUID(),
                            userId,
                            candidateKnowledge,
                            candidateQuality,
                            candidatePreferences,
                            companyKnowledge,
                            companyMeta,
                            companyInsights,
                            jobCache.getStructuredKnowledge(),
                            jobCache.getMetadata(),
                            jobCache.getInsights()
                    );

                    double score = matchResult.getOverallScore();
                    if (score >= minScore) {
                        matchedJobIds.add(job.getId());

                        Map<String, Object> mJob = new HashMap<>();
                        mJob.put("jobId", job.getId().toString());
                        mJob.put("title", job.getTitle());
                        mJob.put("company", job.getCompany());
                        mJob.put("matchScore", score);
                        matchedJobsList.add(mJob);
                    }
                }

                eventPublisher.publishEvent(JobsMatchedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .correlationId(UUID.fromString(context.getCorrelationId()))
                        .workflowId(context.getWorkflowId())
                        .userId(userId)
                        .build());

                Map<String, Object> outputData = new HashMap<>();
                outputData.put("matchedJobs", matchedJobsList);
                outputData.put("matchedJobIds", matchedJobIds.stream().map(UUID::toString).collect(Collectors.toList()));
                outputData.put("matchedCount", matchedJobIds.size());

                return AgentResult.builder()
                        .status(AgentResult.Status.SUCCESS)
                        .message("Evaluated matches. Found " + matchedJobIds.size() + " jobs matching min score " + minScore + "%")
                        .outputData(outputData)
                        .build();
            }

        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed executing task type " + type + ": " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    private boolean matchesCriteria(DiscoveryJob job, JobSearchCriteria criteria) {
        if (job == null) return false;

        String title = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        String loc = job.getLocation() != null ? job.getLocation().toLowerCase() : "";
        String content = job.getRawContent() != null ? job.getRawContent().toLowerCase() : "";

        boolean roleMatch = criteria.getPreferredRoles().stream()
                .anyMatch(role -> title.contains(role.toLowerCase()) || role.toLowerCase().contains(title));

        boolean locMatch = criteria.getLocations().stream()
                .anyMatch(l -> loc.contains(l.toLowerCase()) || l.toLowerCase().contains(loc));

        boolean keywordMatch = criteria.getKeywords().stream()
                .anyMatch(k -> title.contains(k.toLowerCase()) || content.contains(k.toLowerCase()));

        boolean skillMatch = criteria.getSkills().stream()
                .anyMatch(s -> title.contains(s.toLowerCase()) || content.contains(s.toLowerCase()));

        return roleMatch || locMatch || keywordMatch || skillMatch;
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
