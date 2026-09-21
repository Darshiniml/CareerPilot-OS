package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ingestion and evidence-based matching for inbound HR communications.
 *
 * <p>Foundation scope only: classification is always persisted as {@link CommunicationClassification#UNKNOWN}
 * (the AI classifier is a later stage), no application workflow state is mutated, and no notifications or
 * follow-up recommendations are generated during ingestion.</p>
 */
@Service
@RequiredArgsConstructor
public class HrCommunicationService {

    private final HrCommunicationRepository communicationRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationCommunicationMatcher matcher;

    @Transactional
    public HrCommunication ingest(UUID candidateId,
                                  CommunicationProvider provider,
                                  String externalMessageId,
                                  String threadId,
                                  String sender,
                                  String recipient,
                                  String subject,
                                  String body,
                                  Instant receivedAt) {

        Optional<HrCommunication> existing = communicationRepository.findByProviderAndExternalMessageId(provider, externalMessageId);
        if (existing.isPresent()) {
            HrCommunication found = existing.get();
            if (found.getCandidateId().equals(candidateId)) {
                return found;
            }
            throw new DuplicateCommunicationException(
                    "A communication with this provider and external message id already exists");
        }

        Instant now = Instant.now();
        HrCommunication communication = HrCommunication.builder()
                .id(UUID.randomUUID())
                .candidateId(candidateId)
                .provider(provider)
                .externalMessageId(externalMessageId)
                .threadId(threadId)
                .sender(sender)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .receivedAt(receivedAt)
                .classification(CommunicationClassification.UNKNOWN)
                .processingStatus(CommunicationProcessingStatus.RECEIVED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        communicationRepository.save(communication);

        communication.setProcessingStatus(CommunicationProcessingStatus.PROCESSING);
        communication.setUpdatedAt(Instant.now());
        communicationRepository.save(communication);

        List<ApplicationRecord> applications = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        CommunicationMatchResult result = matcher.match(communication, applications);

        if (result.matched()) {
            communication.setMatchedApplicationId(result.applicationId());
            communication.setMatchConfidence(result.confidence());
            communication.setMatchEvidence(result.evidence());
            communication.setProcessingStatus(CommunicationProcessingStatus.PROCESSED);
        } else {
            communication.setMatchedApplicationId(null);
            communication.setMatchConfidence(null);
            communication.setMatchEvidence(result.evidence());
            communication.setProcessingStatus(CommunicationProcessingStatus.UNMATCHED);
        }
        communication.setUpdatedAt(Instant.now());
        return communicationRepository.save(communication);
    }

    @Transactional(readOnly = true)
    public List<HrCommunication> listForCandidate(UUID candidateId) {
        return communicationRepository.findByCandidateIdOrderByReceivedAtDesc(candidateId);
    }

    @Transactional(readOnly = true)
    public Optional<HrCommunication> getForCandidate(UUID candidateId, UUID id) {
        return communicationRepository.findByIdAndCandidateId(id, candidateId);
    }

    @Transactional(readOnly = true)
    public Optional<HrCommunication> findById(UUID id) {
        return communicationRepository.findById(id);
    }
}
