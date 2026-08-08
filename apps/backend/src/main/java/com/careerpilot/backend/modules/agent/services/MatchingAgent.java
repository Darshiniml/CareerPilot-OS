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
import com.careerpilot.backend.modules.discovery.services.JobDiscoveryService;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.events.JobsMatchedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingAgent implements CareerAgent {

    private final JobDiscoveryService jobDiscoveryService;
    private final MatchingEngine matchingEngine;
    private final DataCollector dataCollector;
    private final JobIntelligenceCacheRepository jobCacheRepository;
    private final CompanyIntelligenceCacheRepository companyCacheRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MatchingAgent(JobDiscoveryService jobDiscoveryService,
                         MatchingEngine matchingEngine,
                         DataCollector dataCollector,
                         JobIntelligenceCacheRepository jobCacheRepository,
                         CompanyIntelligenceCacheRepository companyCacheRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.matchingEngine = matchingEngine;
        this.dataCollector = dataCollector;
        this.jobCacheRepository = jobCacheRepository;
        this.companyCacheRepository = companyCacheRepository;
        this.eventPublisher = eventPublisher;
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
        return List.of("JOB_MATCHING");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        AgentPolicy policy = context.getPolicy();
        double minScore = policy != null ? policy.getMinimumMatchScore() : 70.0;
        
        try {
            // 1. Collect candidate data (resume knowledge, preferences, etc.)
            Map<String, Object> candidateData = dataCollector.collectCandidateData(userId);
            ResumeIntelligenceCache resumeCache = (ResumeIntelligenceCache) candidateData.get("resumeCache");
            UserPreference preferences = (UserPreference) candidateData.get("preferences");
            
            if (resumeCache == null) {
                return AgentResult.builder()
                        .status(AgentResult.Status.FAILED)
                        .message("Candidate resume has not been analyzed yet. Please complete Resume Analysis first.")
                        .build();
            }
            
            // Map Candidate Knowledge & Preferences maps
            Map<String, Object> candidateKnowledge = resumeCache.getStructuredKnowledge();
            Map<String, Object> candidateQuality = resumeCache.getQualityMetrics();
            Map<String, Object> candidatePreferences = new HashMap<>();
            if (preferences != null) {
                candidatePreferences.put("preferredRoles", List.of(preferences.getEmploymentType() != null ? preferences.getEmploymentType() : "FULL_TIME"));
                candidatePreferences.put("preferredLocations", List.of("Remote"));
                candidatePreferences.put("salaryExpectation", preferences.getSalaryMin() != null ? preferences.getSalaryMin() : 0);
                candidatePreferences.put("workMode", preferences.getWorkStyle() != null ? preferences.getWorkStyle() : "REMOTE");
            }
            
            // 2. Fetch discovered jobs
            List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
            List<Map<String, Object>> matchedJobsList = new ArrayList<>();
            List<UUID> matchedJobIds = new ArrayList<>();
            
            for (DiscoveryJob job : jobs) {
                String jobChecksum = job.getContentHash();
                if (jobChecksum == null || jobChecksum.isBlank()) {
                    continue;
                }
                
                // Lookup job intelligence cache
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
                
                // Lookup company intelligence cache (if exists)
                Map<String, Object> companyKnowledge = new HashMap<>();
                Map<String, Object> companyMeta = new HashMap<>();
                Map<String, Object> companyInsights = new HashMap<>();
                
                if (job.getCompany() != null) {
                    String cleanName = job.getCompany().toLowerCase().replaceAll("[^a-z0-9]", "");
                    String companyChecksum = cleanName + "_checksum"; // or resolve correctly
                    Optional<CompanyIntelligenceCache> companyCacheOpt = companyCacheRepository.findById(companyChecksum);
                    if (companyCacheOpt.isPresent()) {
                        companyKnowledge = companyCacheOpt.get().getStructuredKnowledge();
                        companyMeta = companyCacheOpt.get().getMetadata();
                        companyInsights = companyCacheOpt.get().getInsights();
                    }
                }
                
                // 3. Match using matching engine
                MatchResultDto matchResult = matchingEngine.matchCandidateToJob(
                        userId,
                        job.getId(),
                        UUID.randomUUID(), // companyId dummy
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
            
            // 4. Publish Event
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
            
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed to match jobs: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
