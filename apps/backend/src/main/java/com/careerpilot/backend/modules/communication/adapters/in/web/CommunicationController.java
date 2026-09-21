package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.api.CommunicationResponse;
import com.careerpilot.backend.modules.communication.api.IngestCommunicationRequest;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.services.DuplicateCommunicationException;
import com.careerpilot.backend.modules.communication.services.HrCommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communications")
@Tag(name = "HR Communications", description = "Inbound HR communication ingestion and matching")
@RequiredArgsConstructor
public class CommunicationController {

    private final HrCommunicationService communicationService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Ingest an inbound HR communication for the authenticated candidate")
    public ResponseEntity<CommunicationResponse> ingest(@Valid @RequestBody IngestCommunicationRequest request,
                                                        Principal principal) {
        UUID candidateId = getUserId(principal);
        try {
            HrCommunication saved = communicationService.ingest(
                    candidateId,
                    request.getProvider(),
                    request.getExternalMessageId(),
                    request.getThreadId(),
                    request.getSender(),
                    request.getRecipient(),
                    request.getSubject(),
                    request.getBody(),
                    request.getReceivedAt());
            return ResponseEntity.status(HttpStatus.CREATED).body(CommunicationResponse.from(saved));
        } catch (DuplicateCommunicationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping
    @Operation(summary = "List the authenticated candidate's HR communications")
    public ResponseEntity<List<CommunicationResponse>> list(Principal principal) {
        UUID candidateId = getUserId(principal);
        List<CommunicationResponse> body = communicationService.listForCandidate(candidateId).stream()
                .map(CommunicationResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a single HR communication owned by the authenticated candidate")
    public ResponseEntity<CommunicationResponse> get(@PathVariable UUID id, Principal principal) {
        UUID candidateId = getUserId(principal);
        HrCommunication communication = communicationService.findById(id).orElse(null);
        if (communication == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!communication.getCandidateId().equals(candidateId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(CommunicationResponse.from(communication));
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Unauthorized");
        }
        return userRepository.findByEmail(principal.getName())
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
