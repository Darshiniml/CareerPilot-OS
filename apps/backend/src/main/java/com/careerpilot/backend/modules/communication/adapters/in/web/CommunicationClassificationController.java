package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.api.ClassificationResponse;
import com.careerpilot.backend.modules.communication.classification.ClassificationOutcome;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.services.HrCommunicationClassificationService;
import com.careerpilot.backend.modules.communication.services.HrCommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Candidate-scoped AI classification endpoints (Milestone 22.3).
 *
 * <p>Candidate identity always comes from authentication; it is never accepted from the request.
 * The classify endpoint takes no body, so classification, confidence, candidateId and applicationId
 * cannot be supplied by a caller. A communication that does not exist returns 404; one owned by a
 * different candidate returns 403 without leaking any of its content.</p>
 */
@RestController
@RequestMapping("/api/v1/communications")
@Tag(name = "HR Communication Classification", description = "AI classification of inbound HR communications")
@RequiredArgsConstructor
public class CommunicationClassificationController {

    private final HrCommunicationService communicationService;
    private final HrCommunicationClassificationService classificationService;
    private final UserRepository userRepository;

    @PostMapping("/{id}/classify")
    @Operation(summary = "Classify a persisted HR communication owned by the authenticated candidate")
    public ResponseEntity<?> classify(@PathVariable UUID id, Principal principal) {
        UUID candidateId = getUserId(principal);
        HrCommunication communication = communicationService.findById(id).orElse(null);
        if (communication == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!communication.getCandidateId().equals(candidateId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ClassificationOutcome outcome = classificationService.classify(candidateId, id);
        if (!outcome.success()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "AI_CLASSIFICATION_FAILED",
                    "message", outcome.failureReason() == null ? "AI classification failed" : outcome.failureReason(),
                    "processingStatus", outcome.communication().getProcessingStatus().name()));
        }
        return ResponseEntity.ok(ClassificationResponse.from(outcome.communication()));
    }

    @GetMapping("/{id}/classification")
    @Operation(summary = "Retrieve the stored classification for a communication owned by the authenticated candidate")
    public ResponseEntity<?> getClassification(@PathVariable UUID id, Principal principal) {
        UUID candidateId = getUserId(principal);
        HrCommunication communication = communicationService.findById(id).orElse(null);
        if (communication == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!communication.getCandidateId().equals(candidateId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ClassificationResponse.from(communication));
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
