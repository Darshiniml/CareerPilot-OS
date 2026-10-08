package com.careerpilot.backend.modules.followup;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.followup.domain.FollowUpDecision;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDecisionRepository;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDraftRepository;
import com.careerpilot.backend.modules.followup.services.FollowUpRecommendationService;
import com.careerpilot.backend.modules.followup.services.FollowUpRecommendationService.Recommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FollowUpRecommendationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private ApplicationRecordRepository applicationRepository;
    private ApplicationHistoryRepository historyRepository;
    private HrCommunicationRepository communicationRepository;
    private DiscoveryJobRepository jobRepository;
    private FollowUpDecisionRepository decisionRepository;
    private FollowUpDraftRepository draftRepository;
    private FollowUpRecommendationService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRecordRepository.class);
        historyRepository = mock(ApplicationHistoryRepository.class);
        communicationRepository = mock(HrCommunicationRepository.class);
        jobRepository = mock(DiscoveryJobRepository.class);
        decisionRepository = mock(FollowUpDecisionRepository.class);
        draftRepository = mock(FollowUpDraftRepository.class);
        service = new FollowUpRecommendationService(applicationRepository, historyRepository, communicationRepository,
                jobRepository, decisionRepository, draftRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        DiscoveryJob job = new DiscoveryJob();
        job.setId(jobId);
        job.setTitle("Backend Engineer");
        job.setCompany("Acme");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId)).thenReturn(List.of());
        when(decisionRepository.findByUserId(userId)).thenReturn(List.of());
    }

    private ApplicationRecord app(WorkflowState state, Instant submittedAt) {
        ApplicationRecord a = ApplicationRecord.builder().applicationId(UUID.randomUUID()).candidateId(userId)
                .jobId(jobId).workflowState(state).submittedAt(submittedAt).createdAt(NOW.minus(60, ChronoUnit.DAYS)).build();
        when(applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(a));
        return a;
    }

    private HrCommunication email(ApplicationRecord a, CommunicationClassification c, double confidence, Instant at) {
        HrCommunication e = new HrCommunication();
        e.setId(UUID.randomUUID());
        e.setCandidateId(userId);
        e.setMatchedApplicationId(a.getApplicationId());
        e.setClassification(c);
        e.setClassificationConfidence(confidence);
        e.setProcessingStatus(CommunicationProcessingStatus.PROCESSED);
        e.setSender("Priya Recruiter <priya@acme.example>");
        e.setSubject("Update");
        e.setReceivedAt(at);
        when(communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId)).thenReturn(List.of(e));
        return e;
    }

    @Test
    void noFollowUpBeforeSevenDays() {
        app(WorkflowState.SUBMITTED, NOW.minus(5, ChronoUnit.DAYS));
        assertTrue(service.recommend(userId).isEmpty());
    }

    @Test
    void noResponseAfterApplicationIsRecommendedWithEvidence() {
        ApplicationRecord a = app(WorkflowState.SUBMITTED, NOW.minus(9, ChronoUnit.DAYS));
        List<Recommendation> recs = service.recommend(userId);
        assertEquals(1, recs.size());
        Recommendation r = recs.get(0);
        assertEquals("NO_RESPONSE_AFTER_APPLICATION", r.ruleCode());
        assertEquals("MEDIUM", r.urgency());
        assertEquals(a.getApplicationId(), r.applicationId());
        assertEquals("APPLICATION_PORTAL", r.recommendedChannel(), "no recruiter email known -> no invented address");
        assertNull(r.suggestedRecipient());
        assertTrue(r.evidence().stream().anyMatch(e -> e.contains("9 days")));
        assertEquals("Acme", r.company());
    }

    @Test
    void urgencyEscalatesAndStaleApplicationsAreLowPriority() {
        app(WorkflowState.SUBMITTED, NOW.minus(20, ChronoUnit.DAYS));
        assertEquals("HIGH", service.recommend(userId).get(0).urgency());
        app(WorkflowState.SUBMITTED, NOW.minus(60, ChronoUnit.DAYS));
        assertEquals("LOW", service.recommend(userId).get(0).urgency());
    }

    @Test
    void interviewInvitationNeedsAReplyToTheRealSender() {
        ApplicationRecord a = app(WorkflowState.INTERVIEW, NOW.minus(20, ChronoUnit.DAYS));
        HrCommunication e = email(a, CommunicationClassification.INTERVIEW_INVITATION, 0.9, NOW.minus(1, ChronoUnit.DAYS));
        Recommendation r = service.recommend(userId).get(0);
        assertEquals("REPLY_INTERVIEW_INVITATION", r.ruleCode());
        assertEquals("HIGH", r.urgency());
        assertEquals("RECRUITER_RESPONSE", r.suggestedDraftType());
        assertEquals(e.getId(), r.relatedCommunicationId());
        assertEquals("Priya Recruiter <priya@acme.example>", r.suggestedRecipient());
    }

    @Test
    void lowConfidenceEmailNeverDrivesARecommendation() {
        ApplicationRecord a = app(WorkflowState.SUBMITTED, NOW.minus(3, ChronoUnit.DAYS));
        email(a, CommunicationClassification.OFFER, 0.45, NOW.minus(1, ChronoUnit.DAYS));
        assertTrue(service.recommend(userId).isEmpty());
    }

    @Test
    void terminalApplicationsGetNoFollowUp() {
        app(WorkflowState.REJECTED, NOW.minus(30, ChronoUnit.DAYS));
        assertTrue(service.recommend(userId).isEmpty());
    }

    @Test
    void recentSentFollowUpSuppressesRecommendation() {
        ApplicationRecord a = app(WorkflowState.SUBMITTED, NOW.minus(12, ChronoUnit.DAYS));
        when(draftRepository.existsByApplicationIdAndStatusAndSentAtAfter(eq(a.getApplicationId()), eq("SENT"), any()))
                .thenReturn(true);
        assertTrue(service.recommend(userId).isEmpty());
    }

    @Test
    void dismissedRecommendationStaysHidden() {
        app(WorkflowState.SUBMITTED, NOW.minus(12, ChronoUnit.DAYS));
        String key = service.recommend(userId).get(0).key();
        when(decisionRepository.findByUserId(userId)).thenReturn(List.of(FollowUpDecision.builder()
                .recommendationKey(key).decision(FollowUpDecision.DISMISSED).build()));
        assertTrue(service.recommend(userId).isEmpty());
    }

    @Test
    void postInterviewStatusCheckUsesWhenTheStateWasEntered() {
        ApplicationRecord a = app(WorkflowState.INTERVIEW, NOW.minus(40, ChronoUnit.DAYS));
        when(historyRepository.findByApplicationIdOrderByCreatedAtAsc(a.getApplicationId())).thenReturn(List.of(
                ApplicationHistory.builder().toState(WorkflowState.INTERVIEW).createdAt(NOW.minus(12, ChronoUnit.DAYS)).build()));
        Recommendation r = service.recommend(userId).get(0);
        assertEquals("POST_INTERVIEW_STATUS_CHECK", r.ruleCode());
        assertNotEquals("INTERVIEW_THANK_YOU", r.suggestedDraftType(), "a thank-you needs a confirmed interview");
    }
}
