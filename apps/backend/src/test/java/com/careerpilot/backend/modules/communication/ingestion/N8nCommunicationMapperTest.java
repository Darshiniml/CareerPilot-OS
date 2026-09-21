package com.careerpilot.backend.modules.communication.ingestion;

import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class N8nCommunicationMapperTest {

    private final N8nCommunicationMapper mapper = new N8nCommunicationMapper(new N8nExternalCommunicationSource());

    @Test
    void mapsOnlyProviderOwnedFieldsAndStampsN8nProvider() {
        Instant receivedAt = Instant.parse("2026-09-21T10:15:30Z");
        N8nIngestionPayload payload = N8nIngestionPayload.builder()
                .externalMessageId("ext-123")
                .threadId("thread-9")
                .sender("recruiter@acme-corp.com")
                .recipient("candidate@example.com")
                .subject("Your application")
                .body("Body text")
                .receivedAt(receivedAt.toString())
                .metadata(Map.of("mailbox", "primary"))
                .build();

        NormalizedInboundCommunication normalized = mapper.map(payload);

        assertEquals(CommunicationProvider.N8N, normalized.provider());
        assertEquals("ext-123", normalized.externalMessageId());
        assertEquals("thread-9", normalized.threadId());
        assertEquals("recruiter@acme-corp.com", normalized.sender());
        assertEquals("candidate@example.com", normalized.recipient());
        assertEquals("Your application", normalized.subject());
        assertEquals("Body text", normalized.body());
        assertEquals(receivedAt, normalized.receivedAt());
        assertEquals("primary", normalized.sourceMetadata().get("mailbox"));
    }

    @Test
    void acceptsOffsetDateTimeTimestamps() {
        N8nIngestionPayload payload = N8nIngestionPayload.builder()
                .externalMessageId("ext-1")
                .sender("a@b.com")
                .recipient("c@d.com")
                .subject("S")
                .receivedAt("2026-09-21T10:15:30+02:00")
                .build();

        NormalizedInboundCommunication normalized = mapper.map(payload);
        assertEquals(Instant.parse("2026-09-21T08:15:30Z"), normalized.receivedAt());
    }

    @Test
    void rejectsInvalidTimestamp() {
        N8nIngestionPayload payload = N8nIngestionPayload.builder()
                .externalMessageId("ext-1")
                .sender("a@b.com")
                .recipient("c@d.com")
                .subject("S")
                .receivedAt("not-a-timestamp")
                .build();

        assertThrows(MalformedIngestionPayloadException.class, () -> mapper.map(payload));
    }

    @Test
    void normalizedContractCarriesNoServerOwnedState() {
        // The normalized record type must not expose candidateId/classification/match/status fields.
        for (var component : NormalizedInboundCommunication.class.getRecordComponents()) {
            String name = component.getName();
            assertFalse(name.equals("candidateId") || name.equals("classification")
                            || name.equals("matchedApplicationId") || name.equals("processingStatus")
                            || name.equals("createdAt") || name.equals("updatedAt"),
                    "normalized contract must not carry server-owned field: " + name);
        }
    }
}
