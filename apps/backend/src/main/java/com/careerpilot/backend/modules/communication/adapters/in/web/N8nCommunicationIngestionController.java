package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.communication.api.CommunicationResponse;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.ingestion.CommunicationIngestionService;
import com.careerpilot.backend.modules.communication.ingestion.MalformedIngestionPayloadException;
import com.careerpilot.backend.modules.communication.ingestion.N8nCommunicationMapper;
import com.careerpilot.backend.modules.communication.ingestion.N8nIngestionPayload;
import com.careerpilot.backend.modules.communication.ingestion.NormalizedInboundCommunication;
import com.careerpilot.backend.modules.communication.ingestion.UnknownRecipientException;
import com.careerpilot.backend.modules.communication.services.DuplicateCommunicationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Secure machine-to-machine ingestion webhook for n8n.
 *
 * <p>Authentication is enforced upstream by {@code IngestionAuthenticationFilter} (shared-secret,
 * fail-closed); this controller never trusts a caller-supplied identity. The owning candidate is
 * resolved server-side from the message recipient by {@link CommunicationIngestionService}, and all
 * domain persistence/matching is delegated to the existing foundation service.</p>
 */
@RestController
@RequestMapping("/api/v1/communications/ingest")
@Tag(name = "HR Communication Ingestion", description = "External (n8n) communication ingestion webhook")
@RequiredArgsConstructor
public class N8nCommunicationIngestionController {

    private final CommunicationIngestionService ingestionService;
    private final N8nCommunicationMapper mapper;

    @PostMapping("/n8n")
    @Operation(summary = "Ingest a normalized inbound HR communication delivered by n8n")
    public ResponseEntity<CommunicationResponse> ingest(@Valid @RequestBody N8nIngestionPayload payload) {
        try {
            NormalizedInboundCommunication normalized = mapper.map(payload);
            HrCommunication saved = ingestionService.ingest(normalized);
            return ResponseEntity.status(HttpStatus.CREATED).body(CommunicationResponse.from(saved));
        } catch (MalformedIngestionPayloadException ex) {
            return ResponseEntity.badRequest().build();
        } catch (UnknownRecipientException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (DuplicateCommunicationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
