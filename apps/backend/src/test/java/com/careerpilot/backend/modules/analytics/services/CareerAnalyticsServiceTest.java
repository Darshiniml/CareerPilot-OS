package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.analytics.domain.CareerAnalytics;
import com.careerpilot.backend.modules.analytics.repositories.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.profile.domain.Experience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CareerAnalyticsServiceTest {

    private DataCollector dataCollector;
    private MetricCalculator metricCalculator;
    private TrendAnalyzer trendAnalyzer;
    private SkillDemandAnalyzer skillDemandAnalyzer;
    private CareerProgressAnalyzer careerProgressAnalyzer;
    private ApplicationAnalyzer applicationAnalyzer;
    private InterviewAnalyzer interviewAnalyzer;
    private LearningPathEngine learningPathEngine;
    private CareerInsights careerInsights;
    private CareerPredictionEngine predictionEngine;

    private CareerAnalyticsRepository analyticsRepository;
    private SkillGapRepository skillGapRepository;
    private LearningProgressRepository learningProgressRepository;
    private CareerGoalRepository careerGoalRepository;
    private CareerGoalProgressRepository goalProgressRepository;
    private CareerMetricRepository metricRepository;

    private ApplicationEventPublisher eventPublisher;
    private AnalyticsCacheService cacheService;

    private CareerAnalyticsService analyticsService;

    @BeforeEach
    public void setUp() {
        dataCollector = mock(DataCollector.class);
        metricCalculator = mock(MetricCalculator.class);
        trendAnalyzer = mock(TrendAnalyzer.class);
        skillDemandAnalyzer = mock(SkillDemandAnalyzer.class);
        careerProgressAnalyzer = mock(CareerProgressAnalyzer.class);
        applicationAnalyzer = mock(ApplicationAnalyzer.class);
        interviewAnalyzer = mock(InterviewAnalyzer.class);
        learningPathEngine = mock(LearningPathEngine.class);
        careerInsights = mock(CareerInsights.class);
        predictionEngine = mock(CareerPredictionEngine.class);

        analyticsRepository = mock(CareerAnalyticsRepository.class);
        skillGapRepository = mock(SkillGapRepository.class);
        learningProgressRepository = mock(LearningProgressRepository.class);
        careerGoalRepository = mock(CareerGoalRepository.class);
        goalProgressRepository = mock(CareerGoalProgressRepository.class);
        metricRepository = mock(CareerMetricRepository.class);

        eventPublisher = mock(ApplicationEventPublisher.class);
        cacheService = mock(AnalyticsCacheService.class);

        analyticsService = new CareerAnalyticsService(
                dataCollector, metricCalculator, trendAnalyzer, skillDemandAnalyzer,
                careerProgressAnalyzer, applicationAnalyzer, interviewAnalyzer,
                learningPathEngine, careerInsights, predictionEngine,
                analyticsRepository, skillGapRepository, learningProgressRepository,
                careerGoalRepository, goalProgressRepository, metricRepository,
                eventPublisher, cacheService
        );
    }

    @Test
    public void testCalculateAndPersistAnalytics_EmptyHistory() {
        UUID candidateId = UUID.randomUUID();
        when(dataCollector.collectCandidateData(candidateId)).thenReturn(new HashMap<>());
        
        when(skillDemandAnalyzer.analyzeSkills(anySet())).thenReturn(new HashMap<>());
        when(applicationAnalyzer.analyzeApplications(anyList())).thenReturn(new HashMap<>());
        when(interviewAnalyzer.analyzeInterviews(anyList())).thenReturn(new HashMap<>());

        when(analyticsRepository.findByCandidateIdAndAnalysisPeriod(eq(candidateId), eq("MONTHLY")))
                .thenReturn(Optional.empty());
        when(analyticsRepository.save(any(CareerAnalytics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareerAnalytics result = analyticsService.calculateAndPersistAnalytics(candidateId, "MONTHLY");

        assertNotNull(result);
        assertEquals(candidateId, result.getCandidateId());
        assertEquals("MONTHLY", result.getAnalysisPeriod());
        assertEquals(0, result.getTotalJobsDiscovered());
        assertEquals(0, result.getJobsMatched());
        verify(analyticsRepository, times(1)).save(any(CareerAnalytics.class));
    }
}
