package com.careerpilot.backend.modules.analytics.controllers;

import com.careerpilot.backend.modules.analytics.domain.CareerAnalytics;
import com.careerpilot.backend.modules.analytics.services.*;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics Engine", description = "Endpoints for career progression, skill gap analyses, and market trends")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AnalyticsController {

    private final CareerAnalyticsService analyticsService;
    private final CareerExecutionAnalyticsService careerExecutionAnalyticsService;
    private final AdaptiveCareerIntelligenceService adaptiveCareerIntelligenceService;
    private final AdaptiveCareerInsightsService adaptiveCareerInsightsService;
    private final HistoricalSuccessSignalService historicalSuccessSignalService;
    private final DataCollector dataCollector;
    private final SkillDemandAnalyzer skillDemandAnalyzer;
    private final ApplicationAnalyzer applicationAnalyzer;
    private final InterviewAnalyzer interviewAnalyzer;
    private final TrendAnalyzer trendAnalyzer;
    private final UserRepository userRepository;
    private final AnalyticsCacheService cacheService;
    private final CareerProgressAnalyzer careerProgressAnalyzer;

    @GetMapping("/overview")
    @Operation(summary = "Get overall career analytics overview")
    public ResponseEntity<Map<String, Object>> getOverview(Principal principal) {
        UUID candidateId = getUserId(principal);
        String cacheKey = "analytics:overview:" + candidateId;

        Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        CareerAnalytics analytics = analyticsService.calculateAndPersistAnalytics(candidateId, "MONTHLY");

        Map<String, Object> response = new HashMap<>();
        response.put("candidateId", analytics.getCandidateId());
        response.put("analysisPeriod", analytics.getAnalysisPeriod());
        response.put("careerGrowthScore", analytics.getCareerGrowthScore());
        response.put("averageMatchScore", analytics.getAverageMatchScore());
        response.put("averageInterviewReadiness", analytics.getAverageInterviewReadiness());
        response.put("resumeScore", analytics.getResumeScore());
        response.put("skillCoverage", analytics.getSkillCoverage());
        response.put("applicationSuccessRate", analytics.getApplicationSuccessRate());
        response.put("interviewConversionRate", analytics.getInterviewConversionRate());
        response.put("offerConversionRate", analytics.getOfferConversionRate());

        cacheService.put(cacheKey, response, 3600); // 1 hour TTL
        return ResponseEntity.ok(response);
    }

    @GetMapping("/applications")
    public ResponseEntity<Map<String, Object>> getApplicationAnalytics(Principal principal) {
        UUID candidateId = getUserId(principal);
        String cacheKey = "analytics:applications:" + candidateId;

        Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        var apps = (List<com.careerpilot.backend.modules.application.domain.ApplicationRecord>) collected.getOrDefault("applications", List.of());
        Map<String, Object> response = applicationAnalyzer.analyzeApplications(apps);

        cacheService.put(cacheKey, response, 3600);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/matching")
    public ResponseEntity<Map<String, Object>> getMatchingAnalytics(Principal principal) {
        UUID candidateId = getUserId(principal);
        String cacheKey = "analytics:matching:" + candidateId;

        Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        var apps = (List<com.careerpilot.backend.modules.application.domain.ApplicationRecord>) collected.getOrDefault("applications", List.of());
        double avgScore = apps.stream()
                .filter(r -> r.getMatchScore() != null)
                .mapToDouble(com.careerpilot.backend.modules.application.domain.ApplicationRecord::getMatchScore)
                .average()
                .orElse(0.0);

        Map<String, Object> response = new HashMap<>();
        response.put("averageMatchScore", avgScore);
        response.put("topMatchingFactors", List.of("Skills Alignment", "Core Technologies"));
        response.put("weakestMatchingFactors", List.of("Years of Experience"));

        cacheService.put(cacheKey, response, 3600);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/interviews")
    public ResponseEntity<Map<String, Object>> getInterviewAnalytics(Principal principal) {
        UUID candidateId = getUserId(principal);
        String cacheKey = "analytics:interviews:" + candidateId;

        Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        var ints = (List<com.careerpilot.backend.modules.interview.domain.InterviewSession>) collected.getOrDefault("interviews", List.of());
        Map<String, Object> response = interviewAnalyzer.analyzeInterviews(ints);

        cacheService.put(cacheKey, response, 3600);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/skills")
    public ResponseEntity<Map<String, Object>> getSkillAnalytics(Principal principal) {
        UUID candidateId = getUserId(principal);
        String cacheKey = "analytics:skills:" + candidateId;

        Map<String, Object> cached = cacheService.get(cacheKey, Map.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        Set<String> candidateSkills = new HashSet<>();
        var resumeCache = (com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache) collected.get("resumeCache");
        if (resumeCache != null) {
            Map<String, Object> knowledge = resumeCache.getStructuredKnowledge();
            if (knowledge != null && knowledge.containsKey("skills")) {
                List<?> rawSkills = (List<?>) knowledge.get("skills");
                for (Object item : rawSkills) {
                    if (item instanceof Map<?, ?> map) {
                        Object skillVal = map.get("skill");
                        if (skillVal == null) skillVal = map.get("name");
                        if (skillVal != null) candidateSkills.add(skillVal.toString().toLowerCase().trim());
                    } else if (item != null) {
                        candidateSkills.add(item.toString().toLowerCase().trim());
                    }
                }
            }
        }

        Map<String, Object> response = skillDemandAnalyzer.analyzeSkills(candidateSkills);
        cacheService.put(cacheKey, response, 3600);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/market")
    public ResponseEntity<Map<String, Object>> getMarketDemand(Principal principal) {
        UUID candidateId = getUserId(principal);
        Map<String, Object> response = new HashMap<>();
        response.put("roleDemand", Map.of("Backend Engineer", 92.0, "DevOps Engineer", 84.0, "Frontend Engineer", 76.0));
        response.put("locationDemand", Map.of("Bangalore", 88.0, "Remote", 94.0, "Mumbai", 60.0));
        response.put("industryDemand", Map.of("FinTech", 90.0, "HealthTech", 72.0, "E-commerce", 85.0));
        response.put("remoteDemandPercentage", 65.5);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/career-growth")
    public ResponseEntity<Map<String, Object>> getCareerGrowth(Principal principal) {
        UUID candidateId = getUserId(principal);
        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        List<com.careerpilot.backend.modules.profile.domain.Experience> experiences = (List<com.careerpilot.backend.modules.profile.domain.Experience>) collected.getOrDefault("experience", List.of());
        
        Set<String> skills = new HashSet<>();
        var resumeCache = (com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache) collected.get("resumeCache");
        if (resumeCache != null) {
            Map<String, Object> knowledge = resumeCache.getStructuredKnowledge();
            if (knowledge != null && knowledge.containsKey("skills")) {
                List<?> rawSkills = (List<?>) knowledge.get("skills");
                for (Object item : rawSkills) {
                    if (item instanceof Map<?, ?> map) {
                        Object skillVal = map.get("skill");
                        if (skillVal == null) skillVal = map.get("name");
                        if (skillVal != null) skills.add(skillVal.toString().toLowerCase().trim());
                    } else if (item != null) {
                        skills.add(item.toString().toLowerCase().trim());
                    }
                }
            }
        }

        List<com.careerpilot.backend.modules.application.domain.ApplicationRecord> applications = (List<com.careerpilot.backend.modules.application.domain.ApplicationRecord>) collected.getOrDefault("applications", List.of());
        List<com.careerpilot.backend.modules.interview.domain.InterviewSession> interviews = (List<com.careerpilot.backend.modules.interview.domain.InterviewSession>) collected.getOrDefault("interviews", List.of());

        double score = careerProgressAnalyzer.calculateCareerGrowthScore(experiences, skills, applications, interviews);
        
        Map<String, Object> response = new HashMap<>();
        response.put("careerGrowthScore", score);
        response.put("roleProgression", "Tracked successfully");
        response.put("responsibilityProgression", "In progress");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrends(Principal principal, @RequestParam(value = "period", defaultValue = "MONTHLY") String period) {
        UUID candidateId = getUserId(principal);
        Map<String, Object> growthTrend = trendAnalyzer.analyzeTrend(candidateId, "growth_score", period);
        Map<String, Object> matchTrend = trendAnalyzer.analyzeTrend(candidateId, "match_score", period);
        Map<String, Object> readinessTrend = trendAnalyzer.analyzeTrend(candidateId, "readiness_score", period);

        Map<String, Object> response = new HashMap<>();
        response.put("growthScoreTrend", growthTrend);
        response.put("matchScoreTrend", matchTrend);
        response.put("readinessTrend", readinessTrend);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/career-execution")
    @Operation(summary = "Get career execution funnel metrics (Milestone 19)")
    public ResponseEntity<CareerExecutionAnalyticsService.CareerExecutionMetrics> getCareerExecution(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(careerExecutionAnalyticsService.calculateCareerExecution(candidateId));
    }

    @GetMapping("/resume-performance")
    @Operation(summary = "Get resume version performance (Milestone 19)")
    public ResponseEntity<CareerExecutionAnalyticsService.ResumePerformanceReport> getResumePerformance(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(careerExecutionAnalyticsService.calculateResumePerformance(candidateId));
    }

    @GetMapping("/job-source-performance")
    @Operation(summary = "Get job source performance (Milestone 19)")
    public ResponseEntity<CareerExecutionAnalyticsService.SourcePerformanceReport> getSourcePerformance(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(careerExecutionAnalyticsService.calculateSourcePerformance(candidateId));
    }

    @GetMapping("/career-intelligence")
    @Operation(summary = "Get career outcome profile (Milestone 20)")
    public ResponseEntity<AdaptiveCareerIntelligenceService.CareerOutcomeProfile> getCareerIntelligence(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(adaptiveCareerIntelligenceService.getCareerOutcomeProfile(candidateId));
    }

    @GetMapping("/role-performance")
    @Operation(summary = "Get role performance metrics (Milestone 20)")
    public ResponseEntity<List<AdaptiveCareerIntelligenceService.RolePerformance>> getRolePerformance(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(adaptiveCareerIntelligenceService.getRolePerformance(candidateId));
    }

    @GetMapping("/skill-performance")
    @Operation(summary = "Get skill performance metrics (Milestone 20)")
    public ResponseEntity<List<AdaptiveCareerIntelligenceService.SkillPerformance>> getSkillPerformance(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(adaptiveCareerIntelligenceService.getSkillPerformance(candidateId));
    }

    @GetMapping("/company-performance")
    @Operation(summary = "Get company performance metrics (Milestone 20)")
    public ResponseEntity<List<AdaptiveCareerIntelligenceService.CompanyPerformance>> getCompanyPerformance(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(adaptiveCareerIntelligenceService.getCompanyPerformance(candidateId));
    }

    @GetMapping("/location-performance")
    @Operation(summary = "Get location and work mode performance metrics (Milestone 20)")
    public ResponseEntity<Map<String, Object>> getLocationPerformance(Principal principal) {
        UUID candidateId = getUserId(principal);
        Map<String, Object> result = new HashMap<>();
        result.put("locationPerformance", adaptiveCareerIntelligenceService.getLocationPerformance(candidateId));
        result.put("remoteTypePerformance", adaptiveCareerIntelligenceService.getRemoteTypePerformance(candidateId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/source-performance")
    @Operation(summary = "Get source performance metrics (Milestone 20)")
    public ResponseEntity<List<AdaptiveCareerIntelligenceService.SourcePerformance>> getSourcePerformanceM20(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(adaptiveCareerIntelligenceService.getSourcePerformance(candidateId));
    }

    @GetMapping("/adaptive-insights")
    @Operation(summary = "Get adaptive career insights (Milestone 20)")
    public ResponseEntity<List<AdaptiveCareerInsightsService.CareerInsight>> getAdaptiveInsights(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(adaptiveCareerInsightsService.generateAdaptiveInsights(candidateId));
    }

    @GetMapping("/historical-success")
    @Operation(summary = "Get historical success signal (Milestone 20)")
    public ResponseEntity<HistoricalSuccessSignalService.HistoricalSuccessSignal> getHistoricalSuccess(
            Principal principal,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String workMode,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Set<String> skills,
            @RequestParam(required = false) UUID resumeId,
            @RequestParam(required = false) Integer resumeVersion) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(historicalSuccessSignalService.getHistoricalSignal(
                candidateId, title, company, location, workMode, source, skills, resumeId, resumeVersion));
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Unauthorized");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
