package com.careerpilot.backend.modules.communication.ingestion;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.services.HrCommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provider-neutral ingestion orchestration. Sits between an {@link ExternalCommunicationSource}
 * adapter and the existing {@link HrCommunicationService}; it adds NO domain logic of its own.
 *
 * <p>Its single responsibility beyond delegation is authoritative candidate resolution: the owning
 * candidate is derived from the message recipient address (looked up against real users), never from
 * any caller-supplied identifier. An unresolved recipient is rejected rather than defaulted.</p>
 */
@Service
@RequiredArgsConstructor
public class CommunicationIngestionService {

    private final UserRepository userRepository;
    private final HrCommunicationService hrCommunicationService;

    @Transactional
    public HrCommunication ingest(NormalizedInboundCommunication message) {
        UUID candidateId = resolveCandidate(message.recipient());
        return hrCommunicationService.ingest(
                candidateId,
                message.provider(),
                message.externalMessageId(),
                message.threadId(),
                message.sender(),
                message.recipient(),
                message.subject(),
                message.body(),
                message.receivedAt());
    }

    private UUID resolveCandidate(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new UnknownRecipientException("Inbound communication has no recipient to resolve a candidate from");
        }
        return userRepository.findByEmail(recipient.trim())
                .map(User::getId)
                .orElseThrow(() -> new UnknownRecipientException(
                        "Recipient does not correspond to a CareerPilot candidate"));
    }
}
