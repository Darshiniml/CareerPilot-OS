package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.analytics.domain.*;
import com.careerpilot.backend.modules.analytics.repositories.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.profile.domain.Experience;
import com.careerpilot.shared.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerAnalyticsService {
    private final com.careerpilot.backend.modules.analytics.repositories.LearningPathRepository learningPathRepository;

    private final DataCollector dataCollector;
    private final MetricCalculator metricCalculator;
    private final TrendAnalyzer trendAnalyzer;
    private final SkillDemandAnalyzer skillDemandAnalyzer;
    private final CareerProgressAnalyzer careerProgressAnalyzer;
    private final ApplicationAnalyzer applicationAnalyzer;
    private final InterviewAnalyzer interviewAnalyzer;
    private final CareerInsights careerInsights;

    private final CareerAnalyticsRepository analyticsRepository;
    private final SkillGapRepository skillGapRepository;
    private final LearningProgressRepository learningProgressRepository;
    private final CareerGoalRepository careerGoalRepository;
    private final CareerGoalProgressRepository goalProgressRepository;
    private final CareerMetricRepository metricRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final AnalyticsCacheService cacheService;

    @Transactional
    public CareerAnalytics calculateAndPersistAnalytics(UUID candidateId, String period) {
        log.info("Calculating career analytics for candidate {} for period {}", candidateId, period);

        // Collect raw data
        Map<String, Object> collected = dataCollector.collectCandidateData(candidateId);
        List<ApplicationRecord> applications = (List<ApplicationRecord>) collected.getOrDefault("applications", List.of());
        List<InterviewSession> interviews = (List<InterviewSession>) collected.getOrDefault("interviews", List.of());
        List<Experience> experiences = (List<Experience>) collected.getOrDefault("experience", List.of());

        // Extract skills
        var resumeCache = (com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache) collected.get("resumeCache");
        Set<String> candidateSkills = new LinkedHashSet<>();
        double resumeAtsScore = 0.0;
        if (resumeCache != null) {
            Map<String, Object> knowledge = resumeCache.getStructuredKnowledge();
            if (knowledge != null && knowledge.containsKey("skills")) {
                List<?> skillsList = (List<?>) knowledge.get("skills");
                for (Object item : skillsList) {
                    if (item instanceof Map<?, ?> map) {
                        Object skillVal = map.get("skill");
                        if (skillVal == null) skillVal = map.get("name");
                        if (skillVal != null) {
                            candidateSkills.add(skillVal.toString().toLowerCase().trim());
                        }
                    } else if (item != null) {
                        candidateSkills.add(item.toString().toLowerCase().trim());
                    }
                }
            }
            Map<String, Object> metrics = resumeCache.getQualityMetrics();
            if (metrics != null && metrics.containsKey("atsScore")) {
                Object score = metrics.get("atsScore");
                if (score instanceof Number num) {
                    resumeAtsScore = num.doubleValue();
                }
            }
        }

        // Run analyzers
        Map<String, Object> skillAnalysis = skillDemandAnalyzer.analyzeSkills(candidateSkills);
        Map<String, Object> appAnalysis = applicationAnalyzer.analyzeApplications(applications);
        Map<String, Object> intAnalysis = interviewAnalyzer.analyzeInterviews(interviews);

        double careerGrowthScore = careerProgressAnalyzer.calculateCareerGrowthScore(
                experiences, candidateSkills, applications, interviews);

        double appSuccessRate = (double) appAnalysis.getOrDefault("submissionSuccessRate", 0.0);
        double interviewConversionRate = (double) appAnalysis.getOrDefault("interviewConversionRate", 0.0);
        double offerConversionRate = (double) appAnalysis.getOrDefault("offerConversionRate", 0.0);

        double avgMatch = metricCalculator.calculateAverageMatchScore(applications);
        double avgReadiness = metricCalculator.calculateAverageInterviewReadiness(interviews);

        List<String> topSkills = candidateSkills.stream().limit(5).collect(Collectors.toList());
        List<String> missingSkills = (List<String>) skillAnalysis.getOrDefault("missingSkills", List.of());
        List<String> trendingSkills = ((List<Map<String, Object>>) skillAnalysis.getOrDefault("frequentlyRequested", List.of()))
                .stream()
                .map(m -> m.get("skill").toString())
                .collect(Collectors.toList());

        // Target skills coverage
        double skillCoverage = metricCalculator.calculateSkillCoverage(candidateSkills, new HashSet<>(trendingSkills));

        // Persist gaps and fire events
        skillGapRepository.deleteByCandidateId(candidateId);
        Map<String, Double> demandPercentages = (Map<String, Double>) skillAnalysis.getOrDefault("demandPercentages", Map.of());
        for (String missingSkill : missingSkills) {
            double demand = demandPercentages.getOrDefault(missingSkill.toLowerCase(), 0.0);
            String priority = demand >= 50.0 ? "HIGH" : (demand >= 20.0 ? "MEDIUM" : "LOW");

            SkillGap gap = SkillGap.builder()
                    .id(UUID.randomUUID())
                    .candidateId(candidateId)
                    .skill(missingSkill)
                    .demandPercentage(demand)
                    .currentLevel("MISSING")
                    .matchImprovementPotential(demand * 0.1)
                    .priority(priority)
                    .build();
            skillGapRepository.save(gap);

            // Publish Event
            eventPublisher.publishEvent(SkillGapDetectedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .candidateId(candidateId)
                    .skillName(missingSkill)
                    .demandPercentage(demand)
                    .priority(priority)
                    .build());
        }

        // Record metrics for trends
        recordMetric(candidateId, "growth_score", careerGrowthScore);
        recordMetric(candidateId, "match_score", avgMatch);
        recordMetric(candidateId, "readiness_score", avgReadiness);

        // Fetch or create analytics entity
        CareerAnalytics analytics = analyticsRepository.findByCandidateIdAndAnalysisPeriod(candidateId, period)
                .orElse(CareerAnalytics.builder()
                        .id(UUID.randomUUID())
                        .candidateId(candidateId)
                        .analysisPeriod(period)
                        .build());

        analytics.setTotalJobsDiscovered((int) (long) collected.getOrDefault("totalJobsDiscovered", 0L));
        analytics.setJobsMatched(applications.size());
        analytics.setApplicationsSubmitted((int) (long) appAnalysis.getOrDefault("submitted", 0L));
        analytics.setApplicationsSuccessful((int) (long) appAnalysis.getOrDefault("successfulSubmissions", 0L));
        analytics.setInterviewsReceived((int) (long) appAnalysis.getOrDefault("successfulSubmissions", 0L) > 0 ? interviews.size() : 0);
        analytics.setOffersReceived((int) applications.stream().filter(r -> r.getWorkflowState() == com.careerpilot.backend.modules.application.domain.WorkflowState.OFFER).count());
        analytics.setAverageMatchScore(avgMatch);
        analytics.setAverageInterviewReadiness(avgReadiness);
        analytics.setResumeScore(resumeAtsScore);
        analytics.setSkillCoverage(skillCoverage);
        analytics.setApplicationSuccessRate(appSuccessRate);
        analytics.setInterviewConversionRate(interviewConversionRate);
        analytics.setOfferConversionRate(offerConversionRate);
        analytics.setTopSkills(String.join(",", topSkills));
        analytics.setMissingSkills(String.join(",", missingSkills.stream().limit(10).toList()));
        analytics.setTrendingSkills(String.join(",", trendingSkills.stream().limit(10).toList()));
        analytics.setCareerGrowthScore(careerGrowthScore);
        analytics.setUpdatedAt(Instant.now());

        CareerAnalytics saved = analyticsRepository.save(analytics);

        // Invalidate Cache for this user
        cacheService.invalidate("analytics:overview:" + candidateId);
        cacheService.invalidate("analytics:skills:" + candidateId);
        cacheService.invalidate("analytics:matching:" + candidateId);
        cacheService.invalidate("analytics:applications:" + candidateId);
        cacheService.invalidate("analytics:interviews:" + candidateId);

        return saved;
    }

    private void recordMetric(UUID candidateId, String metricType, double value) {
        metricRepository.save(CareerMetric.builder()
                .id(UUID.randomUUID())
                .candidateId(candidateId)
                .metricType(metricType)
                .metricValue(value)
                .build());
    }

    @Transactional
    public CareerGoal createGoal(UUID candidateId, String role, String industry, Double salary, String location, Integer timelineMonths) {
        CareerGoal goal = CareerGoal.builder()
                .id(UUID.randomUUID())
                .candidateId(candidateId)
                .targetRole(role)
                .targetIndustry(industry)
                .targetSalary(salary)
                .targetLocation(location)
                .timelineMonths(timelineMonths)
                .build();

        CareerGoal savedGoal = careerGoalRepository.save(goal);

        CareerGoalProgress progress = CareerGoalProgress.builder()
                .id(UUID.randomUUID())
                .goalId(savedGoal.getId())
                .currentState("FRESHER/BEGINNER")
                .requiredSkills("java,spring-boot,sql,aws")
                .requiredExperience("None")
                .requiredProjects("Create backend portfolio application")
                .learningPlanProgress(0.0)
                .overallProgress(5.0)
                .isCompleted(false)
                .build();
        goalProgressRepository.save(progress);

        eventPublisher.publishEvent(CareerGoalCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .candidateId(candidateId)
                .goalId(savedGoal.getId())
                .targetRole(role)
                .build());

        return savedGoal;
    }

    @Transactional
    public CareerGoalProgress updateGoalProgress(UUID goalId, double learningProgress, double overallProgress, boolean isCompleted) {
        CareerGoalProgress progress = goalProgressRepository.findByGoalId(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal progress not found"));

        progress.setLearningPlanProgress(learningProgress);
        progress.setOverallProgress(overallProgress);
        progress.setCompleted(isCompleted);
        progress.setUpdatedAt(Instant.now());

        CareerGoalProgress saved = goalProgressRepository.save(progress);

        CareerGoal goal = careerGoalRepository.findById(goalId).orElse(null);
        if (goal != null) {
            eventPublisher.publishEvent(CareerGoalProgressUpdatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .candidateId(goal.getCandidateId())
                    .goalId(goalId)
                    .progressPercentage(overallProgress)
                    .build());
        }

        return saved;
    }

    @Transactional
    public LearningProgress updateLearningProgress(UUID candidateId, String skill, double progressPercent, String status) {
        LearningProgress progress = learningProgressRepository.findByCandidateIdAndSkill(candidateId, skill)
                .orElse(LearningProgress.builder()
                        .id(UUID.randomUUID())
                        .candidateId(candidateId)
                        .skill(skill)
                        .build());

        progress.setProgressPercentage(progressPercent);
        progress.setStatus(status);
        progress.setUpdatedAt(Instant.now());

        LearningProgress saved = learningProgressRepository.save(progress);

        // Notify listeners when the skill belongs to an existing learning path.
        learningPathRepository.findByCandidateIdAndSkill(candidateId, skill).ifPresent(path ->
                eventPublisher.publishEvent(LearningProgressUpdatedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .candidateId(candidateId)
                        .learningPathId(path.getId())
                        .progressPercentage(progressPercent)
                        .build()));

        return saved;
    }
}
