package com.careerpilot.backend.modules.communication.ingestion;

import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-neutral representation of an inbound communication as delivered by an external source.
 *
 * <p>Carries ONLY provider-delivered fields. Server-owned state (candidate identity, classification,
 * match result, processing status, timestamps, version) is deliberately absent and is derived by
 * CareerPilot — it can never be supplied by an external caller.</p>
 */
public record NormalizedInboundCommunication(
        CommunicationProvider provider,
        String externalMessageId,
        String threadId,
        String sender,
        String recipient,
        String subject,
        String body,
        Instant receivedAt,
        Map<String, String> sourceMetadata
) {
}
