package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
import com.careerpilot.backend.modules.analytics.domain.*;
import com.careerpilot.backend.modules.analytics.repositories.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.profile.domain.Experience;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AnalyticsComponentsTest {

    @Test
    public void testApplicationAnalytics() {
        MetricCalculator calculator = new MetricCalculator();
        ApplicationAnalyzer analyzer = new ApplicationAnalyzer(calculator);

        UUID candidateId = UUID.randomUUID();
        List<ApplicationRecord> apps = List.of(
                ApplicationRecord.builder().applicationId(UUID.randomUUID()).candidateId(candidateId).workflowState(WorkflowState.SUBMITTED).submittedAt(Instant.now()).matchScore(85.0).build(),
                ApplicationRecord.builder().applicationId(UUID.randomUUID()).candidateId(candidateId).workflowState(WorkflowState.INTERVIEW).submittedAt(Instant.now()).matchScore(90.0).build(),
                ApplicationRecord.builder().applicationId(UUID.randomUUID()).candidateId(candidateId).workflowState(WorkflowState.MATCHED).matchScore(70.0).build()
        );

        Map<String, Object> metrics = analyzer.analyzeApplications(apps);
        assertEquals(3L, metrics.get("totalApplications"));
        assertEquals(2L, metrics.get("submitted"));
        assertEquals(2L, metrics.get("successfulSubmissions"));
        assertEquals(100.0, metrics.get("submissionSuccessRate"));
        assertEquals(81.66, (double) metrics.get("averageMatchScore"), 0.1);
    }

    @Test
    public void testMatchingAnalytics() {
        UUID candidateId = UUID.randomUUID();
        List<ApplicationRecord> apps = List.of(
                ApplicationRecord.builder().candidateId(candidateId).matchScore(80.0).build(),
                ApplicationRecord.builder().candidateId(candidateId).matchScore(70.0).build()
        );
        MetricCalculator calculator = new MetricCalculator();
        double avg = calculator.calculateAverageMatchScore(apps);
        assertEquals(75.0, avg);
    }

    @Test
    public void testSkillDemandAnalyzerAndSkillGap() {
        JobIntelligenceCacheRepository jobRepo = mock(JobIntelligenceCacheRepository.class);
        SkillDemandSnapshotRepository snapshotRepo = mock(SkillDemandSnapshotRepository.class);
        SkillDemandAnalyzer analyzer = new SkillDemandAnalyzer(jobRepo, snapshotRepo);

        Map<String, Object> job1Knowledge = Map.of("skills", List.of(Map.of("skill", "Java"), Map.of("skill", "AWS")));
        Map<String, Object> job2Knowledge = Map.of("skills", List.of(Map.of("skill", "Java"), Map.of("skill", "Docker")));
        
        when(jobRepo.findAll()).thenReturn(List.of(
                JobIntelligenceCache.builder().checksumSha256("hash1").structuredKnowledge(job1Knowledge).build(),
                JobIntelligenceCache.builder().checksumSha256("hash2").structuredKnowledge(job2Knowledge).build()
        ));

        Set<String> candidateSkills = Set.of("java");
        Map<String, Object> result = analyzer.analyzeSkills(candidateSkills);

        assertNotNull(result);
        List<String> missing = (List<String>) result.get("missingSkills");
        assertTrue(missing.contains("aws"));
        assertTrue(missing.contains("docker"));
        assertFalse(missing.contains("java"));

        Map<String, Double> demand = (Map<String, Double>) result.get("demandPercentages");
        assertEquals(100.0, demand.get("java"));
        assertEquals(50.0, demand.get("aws"));
    }

    @Test
    public void testCareerGrowthAnalyzer() {
        CareerProgressAnalyzer analyzer = new CareerProgressAnalyzer();

        List<Experience> experiences = List.of(
                Experience.builder().title("Junior Developer").build(),
                Experience.builder().title("Senior Developer").build()
        );
        Set<String> skills = Set.of("java", "spring", "aws", "docker");
        List<InterviewSession> interviews = List.of(
                InterviewSession.builder().overallReadiness(0.8).build()
        );

        double growthScore = analyzer.calculateCareerGrowthScore(experiences, skills, List.of(), interviews);
        assertEquals(70.0, growthScore, 0.1);
    }

    @Test
    public void testTrendAnalyzer() {
        CareerMetricRepository metricRepo = mock(CareerMetricRepository.class);
        TrendAnalyzer analyzer = new TrendAnalyzer(metricRepo);

        UUID candidateId = UUID.randomUUID();
        Instant now = Instant.now();
        List<CareerMetric> metrics = List.of(
                CareerMetric.builder().candidateId(candidateId).metricType("growth_score").metricValue(60.0).recordedAt(now.minus(10, java.time.temporal.ChronoUnit.DAYS)).build(),
                CareerMetric.builder().candidateId(candidateId).metricType("growth_score").metricValue(70.0).recordedAt(now).build()
        );

        when(metricRepo.findByCandidateIdAndMetricTypeOrderByRecordedAtAsc(candidateId, "growth_score"))
                .thenReturn(metrics);

        Map<String, Object> trends = analyzer.analyzeTrend(candidateId, "growth_score", "WEEKLY");
        assertEquals("WEEKLY", trends.get("period"));
        assertEquals(70.0, (double) trends.get("current"), 0.1);
        assertEquals(60.0, (double) trends.get("previous"), 0.1);
        assertEquals(10.0, (double) trends.get("change"), 0.1);
    }
}
