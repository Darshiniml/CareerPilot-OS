package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.matching.MatchService;

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
    private final MatchService matchService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final ApplicationEventPublisher eventPublisher;
    private final JobDiscoveryAgent jobDiscoveryAgent;
    private final OpportunityPrioritizationService prioritizationService;

    public MatchingAgent(JobDiscoveryService jobDiscoveryService,
                         MatchService matchService,
                         CandidateKnowledgeService candidateKnowledgeService,
                         ApplicationEventPublisher eventPublisher,
                         JobDiscoveryAgent jobDiscoveryAgent,
                         OpportunityPrioritizationService prioritizationService) {
        this.jobDiscoveryService = jobDiscoveryService;
        this.matchService = matchService;
        this.candidateKnowledgeService = candidateKnowledgeService;
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

                if (candidateKnowledgeService.primaryResume(userId).isEmpty()) {
                    return noResumeResult();
                }
                Map<String, Object> matchScores = new HashMap<>();
                for (DiscoveryJob job : jobs) {
                    MatchResultDto matchResult = matchService.match(userId, job.getId(), false);
                    matchScores.put(job.getId().toString(), matchResult.getOverallScore());
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

                if (candidateKnowledgeService.primaryResume(userId).isEmpty()) {
                    return noResumeResult();
                }

                List<DiscoveryJob> jobs = jobDiscoveryService.jobs();
                List<Map<String, Object>> matchedJobsList = new ArrayList<>();
                List<UUID> matchedJobIds = new ArrayList<>();
                int notAnalysed = 0;

                for (DiscoveryJob job : jobs) {
                    // Each job is scored against ITS OWN data only; jobs without AI analysis are scored
                    // on connector facts and flagged, never on another job's intelligence.
                    MatchResultDto matchResult = matchService.match(userId, job.getId(), false);
                    if (Boolean.FALSE.equals(matchResult.getJobAnalyzed())) {
                        notAnalysed++;
                    }
                    double score = matchResult.getOverallScore();
                    if (score >= minScore) {
                        matchedJobIds.add(job.getId());
                        Map<String, Object> mJob = new HashMap<>();
                        mJob.put("jobId", job.getId().toString());
                        mJob.put("title", job.getTitle());
                        mJob.put("company", job.getCompany());
                        mJob.put("matchScore", score);
                        mJob.put("jobAnalyzed", matchResult.getJobAnalyzed());
                        mJob.put("notAssessedFactors", matchResult.getNotAssessedFactors());
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
                outputData.put("jobsWithoutAnalysis", notAnalysed);

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

    private Map<String, Object> candidatePreferences(UserPreference preferences, JobSearchCriteria criteria) {
        Map<String, Object> result = new HashMap<>();
        if (criteria != null) {
            if (criteria.getPreferredRoles() != null && !criteria.getPreferredRoles().isEmpty()) {
                result.put("preferredRoles", criteria.getPreferredRoles());
            }
            if (criteria.getLocations() != null && !criteria.getLocations().isEmpty()) {
                result.put("preferredLocations", criteria.getLocations());
            }
        }
        if (preferences != null) {
            if (preferences.getSalaryMin() != null) {
                result.put("salaryExpectation", preferences.getSalaryMin());
            }
            if (preferences.getWorkStyle() != null) {
                result.put("workMode", preferences.getWorkStyle());
            }
        }
        return result;
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

    private static AgentResult noResumeResult() {
        return AgentResult.builder()
                .status(AgentResult.Status.FAILED)
                .message("Candidate resume has not been analyzed yet. Upload a resume and wait for AI processing to finish.")
                .build();
    }
}
