package com.careerpilot.backend.modules.followup.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.followup.domain.FollowUpDraft;
import com.careerpilot.backend.modules.followup.repositories.EmailSendEventRepository;
import com.careerpilot.backend.modules.followup.services.EmailSendingService;
import com.careerpilot.backend.modules.followup.services.FollowUpDraftService;
import com.careerpilot.backend.modules.followup.services.FollowUpDraftService.CreateDraftCommand;
import com.careerpilot.backend.modules.followup.services.FollowUpRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

/** Follow-up recommendations (M22.5), AI drafts (M22.6) and approved sending (M22.7). */
@RestController
@RequestMapping("/api/v1/follow-ups")
@Tag(name = "Follow-ups", description = "Deterministic follow-up recommendations, AI drafts and approval-gated sending")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpRecommendationService recommendationService;
    private final FollowUpDraftService draftService;
    private final EmailSendingService sendingService;
    private final EmailSendEventRepository eventRepository;
    private final CurrentUser currentUser;

    @GetMapping
    @Operation(summary = "Follow-ups that are due, with reasons and evidence")
    public ResponseEntity<List<FollowUpRecommendationService.Recommendation>> recommendations(Principal principal) {
        return ResponseEntity.ok(recommendationService.recommend(currentUser.requireId(principal)));
    }

    @PostMapping("/decisions")
    public ResponseEntity<Void> decide(@RequestBody DecisionRequest request, Principal principal) {
        recommendationService.decide(currentUser.requireId(principal), request.key(), request.decision(), request.snoozeDays());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drafts")
    @Operation(summary = "Generate an AI draft from stored application facts (never sent automatically)")
    public ResponseEntity<FollowUpDraft> createDraft(@RequestBody CreateDraftRequest request, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        if (request.applicationId() == null) {
            throw new IllegalArgumentException("applicationId is required");
        }
        FollowUpDraft draft = draftService.create(userId, new CreateDraftCommand(request.applicationId(),
                request.draftType(), request.communicationId(), request.recommendationKey(),
                request.userInstructions(), request.interviewCompletedOn()));
        return ResponseEntity.status(HttpStatus.CREATED).body(draft);
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<FollowUpDraft>> drafts(Principal principal) {
        return ResponseEntity.ok(draftService.list(currentUser.requireId(principal)));
    }

    @GetMapping("/drafts/{id}")
    public ResponseEntity<Map<String, Object>> draft(@PathVariable UUID id, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        FollowUpDraft draft = draftService.get(userId, id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("draft", draft);
        body.put("allowedRecipients", sendingService.allowedRecipients(userId, draft.getApplicationId()));
        body.put("events", eventRepository.findByDraftIdOrderByCreatedAtAsc(id));
        return ResponseEntity.ok(body);
    }

    @PutMapping("/drafts/{id}")
    public ResponseEntity<FollowUpDraft> update(@PathVariable UUID id, @RequestBody UpdateDraftRequest request, Principal principal) {
        return ResponseEntity.ok(draftService.update(currentUser.requireId(principal), id,
                request.subject(), request.body(), request.recipient()));
    }

    @PostMapping("/drafts/{id}/discard")
    public ResponseEntity<FollowUpDraft> discard(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(draftService.discard(currentUser.requireId(principal), id));
    }

    @PostMapping("/drafts/{id}/approve")
    @Operation(summary = "Explicitly approve the exact draft text for sending")
    public ResponseEntity<FollowUpDraft> approve(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(sendingService.approve(currentUser.requireId(principal), id));
    }

    @PostMapping("/drafts/{id}/send")
    @Operation(summary = "Send an approved draft from your connected mailbox")
    public ResponseEntity<FollowUpDraft> send(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body,
                                              Principal principal) {
        String provider = body != null ? body.get("provider") : null;
        return ResponseEntity.ok(sendingService.send(currentUser.requireId(principal), id, provider));
    }

    public record DecisionRequest(String key, String decision, Integer snoozeDays) {
    }

    public record CreateDraftRequest(UUID applicationId, String draftType, UUID communicationId, String recommendationKey,
                                     String userInstructions, LocalDate interviewCompletedOn) {
    }

    public record UpdateDraftRequest(String subject, String body, String recipient) {
    }
}
