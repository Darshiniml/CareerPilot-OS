package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.ai.matching.MatchService;
import com.careerpilot.backend.modules.analytics.repositories.LearningProgressRepository;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.services.ApplicationTimelineService;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The Copilot's READ-ONLY tools. Every tool runs for the authenticated user only (the user id is
 * passed by the server, never taken from model output) and returns real stored data. Write actions
 * are never executed here: the Copilot can only suggest them for the user to confirm in the UI.
 */
@Component
@RequiredArgsConstructor
public class CopilotTools {

    static final int MATCH_SCAN_LIMIT = 40;

    private final CandidateKnowledgeService candidateKnowledgeService;
    private final MatchService matchService;
    private final JobContextService jobContextService;
    private final DiscoveryJobRepository jobRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationTimelineService timelineService;
    private final HrCommunicationRepository communicationRepository;
    private final InterviewCoachService interviewCoachService;
    private final LearningProgressRepository learningProgressRepository;
    private final CareerHealthService careerHealthService;
    private final AiGatewayClient gatewayClient;
    private final FollowUpToolBridge followUpToolBridge;
    private final com.careerpilot.backend.modules.ai.matching.SkillGapService skillGapService;

    public record ToolSpec(String name, String description, List<String> arguments) {
    }

    private record Tool(ToolSpec spec, BiFunction<UUID, Map<String, String>, Object> run) {
    }

    private Map<String, Tool> tools;

    private Map<String, Tool> tools() {
        if (tools == null) {
            Map<String, Tool> t = new LinkedHashMap<>();
            add(t, "get_profile_summary", "The candidate's profile, preferences and resume summary", List.of(),
                    (u, a) -> candidateKnowledgeService.candidateSummary(u));
            add(t, "get_resume_insights", "ATS score and AI review of the candidate's primary resume", List.of(),
                    (u, a) -> resumeInsights(u));
            add(t, "search_jobs", "Semantic search over discovered job postings", List.of("query"),
                    (u, a) -> searchJobs(a.get("query")));
            add(t, "get_top_job_matches", "Best-matching recently discovered jobs for the candidate", List.of(),
                    (u, a) -> topMatches(u));
            add(t, "get_job_match", "Detailed match of the candidate against one job", List.of("jobId"),
                    (u, a) -> matchService.match(u, uuid(a.get("jobId")), false));
            add(t, "get_applications", "The candidate's applications and their current states", List.of(),
                    (u, a) -> applications(u));
            add(t, "get_application_timeline", "Full timeline (states + HR emails) of one of the candidate's applications",
                    List.of("applicationId"), (u, a) -> timeline(u, uuid(a.get("applicationId"))));
            add(t, "get_recent_communications", "Recent recruiter/HR emails and how they were classified", List.of(),
                    (u, a) -> communications(u));
            add(t, "get_follow_up_recommendations", "Which applications need a follow-up and why", List.of(),
                    (u, a) -> followUpToolBridge.recommendations(u));
            add(t, "get_interview_readiness", "Interview readiness from the candidate's practice sessions", List.of(),
                    (u, a) -> interviewCoachService.readiness(u));
            add(t, "get_skill_gaps", "Skills most often missing across the candidate's best job matches", List.of(),
                    (u, a) -> skillGaps(u));
            add(t, "get_learning_progress", "Skills the candidate is learning and progress", List.of(),
                    (u, a) -> learningProgressRepository.findByCandidateId(u).stream().map(p -> Map.of(
                            "skill", p.getSkill(), "progressPercentage", p.getProgressPercentage(),
                            "status", Objects.toString(p.getStatus(), ""))).toList());
            add(t, "get_career_health", "Career health score computed from real data", List.of(),
                    (u, a) -> careerHealthService.health(u));
            tools = t;
        }
        return tools;
    }

    private static void add(Map<String, Tool> map, String name, String description, List<String> args,
                            BiFunction<UUID, Map<String, String>, Object> run) {
        map.put(name, new Tool(new ToolSpec(name, description, args), run));
    }

    public List<ToolSpec> catalog() {
        return tools().values().stream().map(Tool::spec).toList();
    }

    public boolean exists(String name) {
        return tools().containsKey(name);
    }

    /** Execute an allow-listed tool for {@code userId}. Unknown arguments are dropped. */
    public Object execute(UUID userId, String name, Map<String, String> arguments) {
        Tool tool = tools().get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        Map<String, String> args = new HashMap<>();
        for (String allowed : tool.spec().arguments()) {
            if (arguments != null && arguments.get(allowed) != null) {
                args.put(allowed, arguments.get(allowed));
            }
        }
        for (String required : tool.spec().arguments()) {
            if (!args.containsKey(required) || args.get(required).isBlank()) {
                throw new IllegalArgumentException("Tool " + name + " requires argument '" + required + "'");
            }
        }
        return tool.run().apply(userId, args);
    }

    // ------------------------------------------------------------------ tool implementations

    private Object resumeInsights(UUID userId) {
        return candidateKnowledgeService.primaryResume(userId).<Object>map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resumeTitle", r.title());
            m.put("version", r.versionNumber());
            m.put("atsScore", r.atsMetrics().get("atsScore"));
            m.put("aiReview", r.atsMetrics().get("aiReview"));
            m.put("details", r.atsMetrics().get("details"));
            return m;
        }).orElse(Map.of("available", false, "reason", "No processed resume"));
    }

    @SuppressWarnings("unchecked")
    private Object searchJobs(String query) {
        Map<String, Object> result = gatewayClient.run("JOB_SEARCH", Map.of("query", query, "documentType", "JOB", "limit", 6));
        List<Map<String, Object>> jobs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> hit : (List<Map<String, Object>>) result.getOrDefault("results", List.of())) {
            String id = String.valueOf(hit.get("documentId"));
            if (!seen.add(id)) continue;
            try {
                jobRepository.findById(UUID.fromString(id)).ifPresent(job -> {
                    Map<String, Object> card = new LinkedHashMap<>(jobContextService.jobCard(job));
                    card.put("relevance", hit.get("score"));
                    jobs.add(card);
                });
            } catch (IllegalArgumentException ignored) {
                // not a discovered job id
            }
        }
        return Map.of("query", query, "jobs", jobs);
    }

    private List<DiscoveryJob> recentJobs() {
        return jobRepository.findAll(PageRequest.of(0, MATCH_SCAN_LIMIT, Sort.by(Sort.Direction.DESC, "discoveredAt"))).getContent();
    }

    private Object topMatches(UUID userId) {
        if (candidateKnowledgeService.primaryResume(userId).isEmpty()) {
            return Map.of("available", false, "reason", "No processed resume; upload one to get matches");
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (DiscoveryJob job : recentJobs()) {
            MatchResultDto m = matchService.match(userId, job.getId(), false);
            Map<String, Object> row = new LinkedHashMap<>(jobContextService.jobCard(job));
            row.put("matchScore", Math.round(m.getOverallScore() * 10) / 10.0);
            row.put("matchedSkills", m.getMatchedSkills());
            row.put("missingSkills", m.getMissingSkills());
            row.put("jobAnalyzed", m.getJobAnalyzed());
            matches.add(row);
        }
        matches.sort(Comparator.comparingDouble(r -> -((Number) r.get("matchScore")).doubleValue()));
        return Map.of("scannedJobs", matches.size(), "topMatches", matches.stream().limit(5).toList());
    }

    private Object skillGaps(UUID userId) {
        return skillGapService.landscape(userId);
    }

    private Object applications(UUID userId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
        Map<String, Long> byState = apps.stream().collect(Collectors.groupingBy(a -> a.getWorkflowState().name(), TreeMap::new, Collectors.counting()));
        List<Map<String, Object>> rows = apps.stream().limit(25).map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applicationId", a.getApplicationId());
            row.put("state", a.getWorkflowState());
            row.put("updatedAt", a.getUpdatedAt());
            row.put("submittedAt", a.getSubmittedAt());
            jobRepository.findById(a.getJobId()).ifPresent(j -> {
                row.put("jobTitle", j.getTitle());
                row.put("company", j.getCompany());
            });
            return row;
        }).toList();
        return Map.of("total", apps.size(), "byState", byState, "applications", rows);
    }

    private Object timeline(UUID userId, UUID applicationId) {
        ApplicationRecord app = applicationRepository.findById(applicationId)
                .filter(a -> userId.equals(a.getCandidateId()))
                .orElseThrow(() -> new NoSuchElementException("Application not found"));
        return Map.of("applicationId", app.getApplicationId(), "state", app.getWorkflowState(),
                "timeline", timelineService.getTimeline(applicationId));
    }

    private Object communications(UUID userId) {
        List<HrCommunication> list = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId);
        return list.stream().limit(15).map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("communicationId", c.getId());
            row.put("receivedAt", c.getReceivedAt());
            row.put("sender", c.getSender());
            row.put("subject", c.getSubject());
            row.put("classification", c.getClassification());
            row.put("classificationConfidence", c.getClassificationConfidence());
            row.put("matchedApplicationId", c.getMatchedApplicationId());
            return row;
        }).toList();
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid id: " + raw);
        }
    }
}
