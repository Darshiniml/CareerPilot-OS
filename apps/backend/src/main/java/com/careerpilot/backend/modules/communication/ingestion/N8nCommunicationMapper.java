package com.careerpilot.backend.modules.communication.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit mapper from the raw n8n webhook payload to the provider-neutral
 * {@link NormalizedInboundCommunication}. Copies provider-owned fields only and stamps the provider
 * from the {@link N8nExternalCommunicationSource}; it never reads or forwards server-owned state.
 */
@Component
@RequiredArgsConstructor
public class N8nCommunicationMapper {

    private final N8nExternalCommunicationSource source;

    public NormalizedInboundCommunication map(N8nIngestionPayload payload) {
        return new NormalizedInboundCommunication(
                source.getProvider(),
                payload.getExternalMessageId(),
                payload.getThreadId(),
                payload.getSender(),
                payload.getRecipient(),
                payload.getSubject(),
                payload.getBody(),
                parseReceivedAt(payload.getReceivedAt()),
                copyMetadata(payload.getMetadata()));
    }

    private Instant parseReceivedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MalformedIngestionPayloadException("receivedAt is required");
        }
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ex) {
                throw new MalformedIngestionPayloadException("receivedAt is not a valid ISO-8601 timestamp");
            }
        }
    }

    private Map<String, String> copyMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(metadata);
    }
}
