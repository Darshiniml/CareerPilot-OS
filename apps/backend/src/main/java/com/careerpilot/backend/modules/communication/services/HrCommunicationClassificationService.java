package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.communication.classification.ClassificationOutcome;
import com.careerpilot.backend.modules.communication.classification.ClassificationResult;
import com.careerpilot.backend.modules.communication.classification.ClassificationResultParser;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI classification of already-persisted HR communications (Milestone 22.3).
 *
 * <p>Reuses the existing AI infrastructure ({@link AiGatewayClient} → the Python ai-service
 * {@code /api/v1/ai/execute} contract) rather than introducing a second integration. The service:</p>
 * <ol>
 *   <li>loads a persisted communication scoped to the authenticated candidate,</li>
 *   <li>builds a request from the communication's actual content plus trusted application context
 *       read from CareerPilot's database (never from the email payload),</li>
 *   <li>invokes the AI gateway and validates the structured response server-side,</li>
 *   <li>persists classification, confidence and evidence only after successful validation,</li>
 *   <li>on any AI failure marks the communication FAILED without fabricating a classification.</li>
 * </ol>
 *
 * <p>This service never mutates application workflow state, sends email, creates notifications,
 * fabricates an application, or changes candidate ownership. Reclassification updates the same
 * communication row (id preserved, optimistic-lock version bumped); it never creates a duplicate.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HrCommunicationClassificationService {

    static final String CLASSIFY_TASK_TYPE = "HR_COMMUNICATION_CLASSIFY";
    private static final int REASON_MAX_LENGTH = 2000;

    private final HrCommunicationRepository communicationRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final DiscoveryJobRepository discoveryJobRepository;
    private final AiGatewayClient aiGatewayClient;
    private final ClassificationResultParser classificationResultParser;

    // Not transactional: the model call must not pin a DB connection. Each save is its own short
    // transaction and returns the entity with its bumped optimistic-lock version, which is kept.
    public ClassificationOutcome classify(UUID candidateId, UUID communicationId) {
        HrCommunication communication = communicationRepository
                .findByIdAndCandidateId(communicationId, candidateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Communication not found for candidate " + candidateId));

        communication.setProcessingStatus(CommunicationProcessingStatus.PROCESSING);
        communication.setUpdatedAt(Instant.now());
        communication = communicationRepository.save(communication);

        AiTaskRequestDto request = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType(CLASSIFY_TASK_TYPE)
                .payload(buildPayload(communication))
                .build();

        ClassificationResult result;
        try {
            AiTaskResponseDto response = aiGatewayClient.executeTask(request);
            result = classificationResultParser.parse(response == null ? null : response.getResult());
        } catch (RuntimeException ex) {
            log.warn("AI classification failed for communication {}: {}", communicationId, ex.getMessage());
            communication.setProcessingStatus(CommunicationProcessingStatus.FAILED);
            communication.setUpdatedAt(Instant.now());
            communicationRepository.save(communication);
            return ClassificationOutcome.failure(communication, ex.getMessage());
        }

        communication.setClassification(result.classification());
        communication.setClassificationConfidence(result.confidence());
        communication.setClassificationReason(buildStoredReason(result));
        communication.setProcessingStatus(CommunicationProcessingStatus.PROCESSED);
        communication.setUpdatedAt(Instant.now());
        HrCommunication saved = communicationRepository.save(communication);
        return ClassificationOutcome.success(saved);
    }

    private Map<String, Object> buildPayload(HrCommunication communication) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subject", communication.getSubject());
        payload.put("body", communication.getBody());
        payload.put("sender", communication.getSender());
        payload.put("recipient", communication.getRecipient());
        payload.put("applicationContext", buildApplicationContext(communication));
        return payload;
    }

    /**
     * Trusted application context sourced only from CareerPilot's database for an application that
     * was already evidence-matched during ingestion. Empty when nothing is matched — the classifier
     * then works from the communication alone and must not fabricate a relationship.
     */
    private Map<String, Object> buildApplicationContext(HrCommunication communication) {
        Map<String, Object> context = new HashMap<>();
        UUID applicationId = communication.getMatchedApplicationId();
        if (applicationId == null) {
            return context;
        }
        applicationRepository.findById(applicationId).ifPresent(application -> {
            if (application.getWorkflowState() != null) {
                context.put("applicationState", application.getWorkflowState().name());
            }
            if (application.getSubmittedAt() != null) {
                context.put("submittedAt", application.getSubmittedAt().toString());
            }
            DiscoveryJob job = discoveryJobRepository.findById(application.getJobId()).orElse(null);
            if (job != null) {
                context.put("company", job.getCompany());
                context.put("jobTitle", job.getTitle());
            }
        });
        return context;
    }

    private String buildStoredReason(ClassificationResult result) {
        String reason = result.reason();
        String evidence = result.evidence();
        String stored = (reason == null || reason.isBlank())
                ? evidence
                : reason + " | evidence: " + evidence;
        if (stored.length() > REASON_MAX_LENGTH) {
            stored = stored.substring(0, REASON_MAX_LENGTH);
        }
        return stored;
    }
}
