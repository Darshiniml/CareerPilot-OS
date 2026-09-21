package com.careerpilot.backend.modules.communication.api;

import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestCommunicationRequest {

    @NotNull(message = "provider is required")
    private CommunicationProvider provider;

    @NotBlank(message = "externalMessageId is required")
    @Size(max = 255, message = "externalMessageId must be at most 255 characters")
    private String externalMessageId;

    @Size(max = 255, message = "threadId must be at most 255 characters")
    private String threadId;

    @NotBlank(message = "sender is required")
    @Email(message = "sender must be a valid email address")
    @Size(max = 320, message = "sender must be at most 320 characters")
    private String sender;

    @Size(max = 320, message = "recipient must be at most 320 characters")
    private String recipient;

    @Size(max = 1000, message = "subject must be at most 1000 characters")
    private String subject;

    private String body;

    @NotNull(message = "receivedAt is required")
    private Instant receivedAt;

    @AssertTrue(message = "either subject or body must be provided")
    public boolean isHasContent() {
        return (subject != null && !subject.isBlank()) || (body != null && !body.isBlank());
    }
}
