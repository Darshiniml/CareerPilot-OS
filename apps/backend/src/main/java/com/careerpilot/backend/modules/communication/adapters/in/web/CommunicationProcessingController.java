package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.api.CommunicationProcessingResponse;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.services.ApplicationStateTransitionService;
import com.careerpilot.backend.modules.communication.services.HrCommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * Candidate-scoped processing of classified HR communications into application intelligence
 * (Milestone 22.4).
 *
 * <p>Candidate identity always comes from authentication. The endpoint takes no body: applicationId,
 * previousState, newState, eventType and actorId are all derived server-side by the deterministic
 * {@link ApplicationStateTransitionService} and cannot be supplied or overridden by a caller.
 * A communication that does not exist returns 404; one owned by a different candidate returns 403
 * without leaking any of its content.</p>
 */
@RestController
@RequestMapping("/api/v1/communications")
@Tag(name = "HR Communication Processing", description = "Deterministic processing of classifications into application timeline and state")
@RequiredArgsConstructor
public class CommunicationProcessingController {

    private final HrCommunicationService communicationService;
    private final ApplicationStateTransitionService transitionService;
    private final UserRepository userRepository;

    @PostMapping("/{id}/process")
    @Operation(summary = "Process a classified communication into application timeline intelligence")
    public ResponseEntity<?> process(@PathVariable UUID id, Principal principal) {
        UUID candidateId = getUserId(principal);
        HrCommunication communication = communicationService.findById(id).orElse(null);
        if (communication == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!communication.getCandidateId().equals(candidateId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ApplicationStateTransitionService.TransitionOutcome outcome =
                transitionService.processClassification(candidateId, id);
        return ResponseEntity.ok(CommunicationProcessingResponse.from(id, outcome));
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
