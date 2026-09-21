package com.careerpilot.backend.modules.communication.ingestion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Raw inbound contract for the n8n ingestion webhook. Contains ONLY provider-delivered fields.
 *
 * <p>Server-owned state is intentionally absent — {@code candidateId}, {@code classification},
 * {@code classificationConfidence}, {@code matchedApplicationId}, {@code processingStatus},
 * {@code createdAt}, {@code updatedAt} are not declared here, so any such keys sent by a caller are
 * ignored on deserialization and can never become authoritative. The owning candidate is resolved
 * server-side from {@code recipient}.</p>
 *
 * <p>{@code receivedAt} is accepted as a raw string and parsed by the mapper so that an invalid
 * timestamp yields a clean 400 rather than an opaque deserialization failure.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nIngestionPayload {

    @NotBlank(message = "externalMessageId is required")
    @Size(max = 255, message = "externalMessageId must be at most 255 characters")
    private String externalMessageId;

    @Size(max = 255, message = "threadId must be at most 255 characters")
    private String threadId;

    @NotBlank(message = "sender is required")
    @Email(message = "sender must be a valid email address")
    @Size(max = 320, message = "sender must be at most 320 characters")
    private String sender;

    @NotBlank(message = "recipient is required")
    @Email(message = "recipient must be a valid email address")
    @Size(max = 320, message = "recipient must be at most 320 characters")
    private String recipient;

    @Size(max = 1000, message = "subject must be at most 1000 characters")
    private String subject;

    private String body;

    @NotBlank(message = "receivedAt is required")
    private String receivedAt;

    private Map<String, String> metadata;

    @AssertTrue(message = "either subject or body must be provided")
    public boolean isHasContent() {
        return (subject != null && !subject.isBlank()) || (body != null && !body.isBlank());
    }
}
