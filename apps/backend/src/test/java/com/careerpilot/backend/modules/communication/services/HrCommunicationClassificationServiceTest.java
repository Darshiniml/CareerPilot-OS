package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiTimeoutException;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.communication.classification.ClassificationOutcome;
import com.careerpilot.backend.modules.communication.classification.ClassificationResultParser;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HrCommunicationClassificationService} using the real
 * {@link ClassificationResultParser} (the validation logic under test) with a mocked AI gateway and
 * mocked repositories. No live AI provider is required.
 */
class HrCommunicationClassificationServiceTest {

    private HrCommunicationRepository communicationRepository;
    private ApplicationRecordRepository applicationRepository;
    private DiscoveryJobRepository discoveryJobRepository;
    private AiGatewayClient aiGatewayClient;

    private HrCommunicationClassificationService service;

    private UUID candidateId;
    private UUID communicationId;
    private HrCommunication communication;

    @BeforeEach
    void setUp() {
        communicationRepository = Mockito.mock(HrCommunicationRepository.class);
        applicationRepository = Mockito.mock(ApplicationRecordRepository.class);
        discoveryJobRepository = Mockito.mock(DiscoveryJobRepository.class);
        aiGatewayClient = Mockito.mock(AiGatewayClient.class);

        service = new HrCommunicationClassificationService(
                communicationRepository,
                applicationRepository,
                discoveryJobRepository,
                aiGatewayClient,
                new ClassificationResultParser());

        candidateId = UUID.randomUUID();
        communicationId = UUID.randomUUID();
        communication = HrCommunication.builder()
                .id(communicationId)
                .candidateId(candidateId)
                .provider(CommunicationProvider.GMAIL)
                .externalMessageId("ext-" + UUID.randomUUID())
                .sender("hr@acme-corp.com")
                .recipient("candidate@example.com")
                .subject("Interview invitation")
                .body("We would like to invite you to interview with the team.")
                .receivedAt(Instant.now())
                .classification(CommunicationClassification.UNKNOWN)
                .processingStatus(CommunicationProcessingStatus.RECEIVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(communicationRepository.findByIdAndCandidateId(communicationId, candidateId))
                .thenReturn(Optional.of(communication));
        when(communicationRepository.save(any(HrCommunication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubAiResult(Map<String, Object> result) {
        when(aiGatewayClient.executeTask(any(AiTaskRequestDto.class)))
                .thenReturn(AiTaskResponseDto.builder()
                        .taskId(UUID.randomUUID())
                        .status("COMPLETED")
                        .provider("hr-communication-classifier")
                        .result(result)
                        .metadata(new HashMap<>())
                        .build());
    }

    private Map<String, Object> validResult(String classification, double confidence) {
        Map<String, Object> result = new HashMap<>();
        result.put("classification", classification);
        result.put("confidence", confidence);
        result.put("evidence", "\"invite you to interview\"");
        result.put("reason", "interview scheduling language");
        result.put("signals", List.of("interview"));
        return result;
    }

    @Test
    void persistsValidClassificationAndMarksProcessed() {
        stubAiResult(validResult("INTERVIEW_INVITATION", 0.92));

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertTrue(outcome.success());
        assertEquals(CommunicationClassification.INTERVIEW_INVITATION, outcome.communication().getClassification());
        assertEquals(0.92, outcome.communication().getClassificationConfidence(), 0.0001);
        assertEquals(CommunicationProcessingStatus.PROCESSED, outcome.communication().getProcessingStatus());
        assertNotNull(outcome.communication().getClassificationReason());
        assertTrue(outcome.communication().getClassificationReason().contains("interview"));
    }

    @Test
    void invalidClassificationFailsSafelyWithoutFabricatingValue() {
        stubAiResult(validResult("HIRED", 0.9));

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertFalse(outcome.success());
        assertEquals(CommunicationProcessingStatus.FAILED, outcome.communication().getProcessingStatus());
        // No fabricated classification persisted — remains the pre-existing UNKNOWN.
        assertEquals(CommunicationClassification.UNKNOWN, outcome.communication().getClassification());
        assertNull(outcome.communication().getClassificationConfidence());
    }

    @Test
    void confidenceAboveRangeFailsSafely() {
        stubAiResult(validResult("OFFER", 1.5));

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertFalse(outcome.success());
        assertEquals(CommunicationProcessingStatus.FAILED, outcome.communication().getProcessingStatus());
        assertEquals(CommunicationClassification.UNKNOWN, outcome.communication().getClassification());
    }

    @Test
    void confidenceBelowRangeFailsSafely() {
        stubAiResult(validResult("OFFER", -0.1));

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertFalse(outcome.success());
        assertEquals(CommunicationProcessingStatus.FAILED, outcome.communication().getProcessingStatus());
    }

    @Test
    void missingEvidenceFailsSafely() {
        Map<String, Object> result = validResult("REJECTION", 0.8);
        result.remove("evidence");
        stubAiResult(result);

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertFalse(outcome.success());
        assertEquals(CommunicationProcessingStatus.FAILED, outcome.communication().getProcessingStatus());
    }

    @Test
    void aiUnavailableIsHonestAndRecoverable() {
        when(aiGatewayClient.executeTask(any(AiTaskRequestDto.class)))
                .thenThrow(new AiTimeoutException("AI service call timed out after 3 attempts"));

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertFalse(outcome.success());
        assertNotNull(outcome.failureReason());
        assertEquals(CommunicationProcessingStatus.FAILED, outcome.communication().getProcessingStatus());
        assertEquals(CommunicationClassification.UNKNOWN, outcome.communication().getClassification());
        // The same communication row is preserved (id unchanged) so it can be retried.
        assertEquals(communicationId, outcome.communication().getId());
    }

    @Test
    void serverOwnedIdentifiersFromAiOutputAreNeverPersisted() {
        UUID attackerCandidate = UUID.randomUUID();
        UUID attackerApplication = UUID.randomUUID();
        Map<String, Object> result = validResult("OFFER", 0.95);
        result.put("candidateId", attackerCandidate.toString());
        result.put("applicationId", attackerApplication.toString());
        result.put("matchedApplicationId", attackerApplication.toString());
        result.put("applicationState", "OFFER");
        result.put("actorId", "admin");
        stubAiResult(result);

        ClassificationOutcome outcome = service.classify(candidateId, communicationId);

        assertTrue(outcome.success());
        HrCommunication saved = outcome.communication();
        assertEquals(candidateId, saved.getCandidateId());
        assertEquals(communicationId, saved.getId());
        assertNull(saved.getMatchedApplicationId());
        assertEquals(CommunicationClassification.OFFER, saved.getClassification());
    }

    @Test
    void applicationContextIsSourcedFromDatabaseNotEmailBody() {
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        communication.setMatchedApplicationId(applicationId);

        ApplicationRecord application = ApplicationRecord.builder()
                .applicationId(applicationId)
                .candidateId(candidateId)
                .jobId(jobId)
                .workflowState(WorkflowState.SUBMITTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .retryCount(0)
                .build();
        DiscoveryJob job = DiscoveryJob.builder()
                .id(jobId)
                .company("Acme Corp")
                .title("Backend Engineer")
                .build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(discoveryJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        stubAiResult(validResult("INTERVIEW_INVITATION", 0.9));

        service.classify(candidateId, communicationId);

        ArgumentCaptor<AiTaskRequestDto> captor = ArgumentCaptor.forClass(AiTaskRequestDto.class);
        verify(aiGatewayClient).executeTask(captor.capture());
        Object context = captor.getValue().getPayload().get("applicationContext");
        assertTrue(context instanceof Map);
        Map<?, ?> ctx = (Map<?, ?>) context;
        assertEquals("Acme Corp", ctx.get("company"));
        assertEquals("Backend Engineer", ctx.get("jobTitle"));
        assertEquals("SUBMITTED", ctx.get("applicationState"));
    }

    @Test
    void unmatchedCommunicationSendsEmptyApplicationContext() {
        communication.setMatchedApplicationId(null);
        stubAiResult(validResult("APPLICATION_RECEIVED", 0.8));

        service.classify(candidateId, communicationId);

        ArgumentCaptor<AiTaskRequestDto> captor = ArgumentCaptor.forClass(AiTaskRequestDto.class);
        verify(aiGatewayClient).executeTask(captor.capture());
        Map<?, ?> ctx = (Map<?, ?>) captor.getValue().getPayload().get("applicationContext");
        assertTrue(ctx.isEmpty());
        // No application is fabricated when nothing is matched.
        verify(applicationRepository, times(0)).findById(any());
    }

    @Test
    void repeatedClassificationUpdatesSameRowWithoutCreatingDuplicate() {
        stubAiResult(validResult("INTERVIEW_INVITATION", 0.9));

        ClassificationOutcome first = service.classify(candidateId, communicationId);
        stubAiResult(validResult("OFFER", 0.95));
        ClassificationOutcome second = service.classify(candidateId, communicationId);

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(communicationId, first.communication().getId());
        assertEquals(communicationId, second.communication().getId());
        assertEquals(CommunicationClassification.OFFER, second.communication().getClassification());

        ArgumentCaptor<HrCommunication> savedCaptor = ArgumentCaptor.forClass(HrCommunication.class);
        verify(communicationRepository, Mockito.atLeastOnce()).save(savedCaptor.capture());
        // Every persisted entity is the same communication id — never a new one.
        assertTrue(savedCaptor.getAllValues().stream().allMatch(c -> communicationId.equals(c.getId())));
    }

    @Test
    void classificationOwnedByAnotherCandidateIsNotLoaded() {
        UUID otherCandidate = UUID.randomUUID();
        when(communicationRepository.findByIdAndCandidateId(communicationId, otherCandidate))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.classify(otherCandidate, communicationId));
        verify(aiGatewayClient, times(0)).executeTask(any());
    }
}
