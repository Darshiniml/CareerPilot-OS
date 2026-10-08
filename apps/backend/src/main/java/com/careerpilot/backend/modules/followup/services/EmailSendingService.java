package com.careerpilot.backend.modules.followup.services;

import com.careerpilot.backend.config.CodedException;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.followup.domain.EmailConnection;
import com.careerpilot.backend.modules.followup.domain.FollowUpDraft;
import com.careerpilot.backend.modules.followup.email.EmailConnectionService;
import com.careerpilot.backend.modules.followup.repositories.EmailConnectionRepository;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/**
 * M22.7: controlled sending. AI draft → user review → explicit approval → send via the user's own
 * OAuth-connected mailbox → audit event. Nothing is ever sent without an approval of the exact text,
 * and recipients are restricted to addresses that wrote to the candidate about that application.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSendingService {

    private final FollowUpDraftService draftService;
    private final FollowUpDraftRepository draftRepository;
    private final EmailConnectionRepository connectionRepository;
    private final EmailConnectionService connectionService;
    private final HrCommunicationRepository communicationRepository;
    private final Clock clock;

    @Transactional
    public FollowUpDraft approve(UUID userId, UUID draftId) {
        FollowUpDraft draft = draftService.owned(userId, draftId);
        if (!FollowUpDraft.DRAFT.equals(draft.getStatus()) && !FollowUpDraft.FAILED.equals(draft.getStatus())) {
            throw new IllegalStateException("Only a DRAFT or FAILED draft can be approved (current: " + draft.getStatus() + ")");
        }
        List<String> placeholders = FollowUpDraftService.placeholders(draft);
        if (!placeholders.isEmpty()) {
            throw new CodedException("Fill in the placeholders before approving: " + String.join(", ", placeholders),
                    "DRAFT_HAS_PLACEHOLDERS", 409, null);
        }
        requireAllowedRecipient(userId, draft);
        draft.setStatus(FollowUpDraft.APPROVED);
        draft.setApprovedAt(clock.instant());
        draft.setUpdatedAt(clock.instant());
        draftService.event(draft, "APPROVED", null, "Approved by the candidate");
        return draftRepository.save(draft);
    }

    @Transactional(noRollbackFor = CodedException.class)
    public FollowUpDraft send(UUID userId, UUID draftId, String provider) {
        FollowUpDraft draft = draftService.owned(userId, draftId);
        if (!FollowUpDraft.APPROVED.equals(draft.getStatus())) {
            throw new IllegalStateException("The draft must be approved before sending (current: " + draft.getStatus() + ")");
        }
        requireAllowedRecipient(userId, draft);
        EmailConnection connection = resolveConnection(userId, provider);

        draft.setStatus(FollowUpDraft.SENDING);
        draft.setProvider(connection.getProvider());
        draft.setUpdatedAt(clock.instant());
        draftRepository.saveAndFlush(draft);
        draftService.event(draft, "SEND_REQUESTED", connection.getProvider(), null);
        try {
            String accessToken = connectionService.accessToken(connection);
            String messageId = connectionService.client(connection.getProvider())
                    .send(accessToken, draft.getRecipient(), draft.getSubject(), draft.getBody());
            draft.setStatus(FollowUpDraft.SENT);
            draft.setSentAt(clock.instant());
            draft.setProviderMessageId(messageId);
            draft.setSendError(null);
            draftService.event(draft, "SENT", connection.getProvider(), messageId != null ? "message id " + messageId : "accepted by provider");
        } catch (RuntimeException e) {
            draft.setStatus(FollowUpDraft.FAILED);
            draft.setSendError(e.getMessage() != null && e.getMessage().length() > 1000 ? e.getMessage().substring(0, 1000) : e.getMessage());
            draftService.event(draft, "FAILED", connection.getProvider(), draft.getSendError());
            log.warn("Sending draft {} failed: {}", draftId, e.getMessage());
        }
        draft.setUpdatedAt(clock.instant());
        return draftRepository.save(draft);
    }

    /** Addresses that emailed the candidate about this application (the only allowed recipients). */
    public Set<String> allowedRecipients(UUID userId, UUID applicationId) {
        Set<String> allowed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId).stream()
                .filter(c -> applicationId.equals(c.getMatchedApplicationId()))
                .map(c -> extractAddress(c.getSender()))
                .filter(Objects::nonNull)
                .forEach(allowed::add);
        return allowed;
    }

    private void requireAllowedRecipient(UUID userId, FollowUpDraft draft) {
        String recipient = extractAddress(draft.getRecipient());
        if (recipient == null) {
            throw new CodedException("No recipient: CareerPilot only emails contacts who wrote to you about this "
                    + "application. Otherwise follow up through the job's application portal.", "NO_ALLOWED_RECIPIENT", 409, null);
        }
        if (!allowedRecipients(userId, draft.getApplicationId()).contains(recipient)) {
            throw new CodedException("Recipient " + recipient + " has not emailed you about this application",
                    "RECIPIENT_NOT_ALLOWED", 403, null);
        }
        draft.setRecipient(recipient);
    }

    private EmailConnection resolveConnection(UUID userId, String provider) {
        List<EmailConnection> connected = connectionRepository.findByUserId(userId).stream()
                .filter(c -> EmailConnection.CONNECTED.equals(c.getStatus()))
                .filter(c -> provider == null || provider.isBlank() || c.getProvider().equalsIgnoreCase(provider))
                .toList();
        if (connected.isEmpty()) {
            throw new CodedException("Connect a Gmail or Outlook mailbox in Settings before sending",
                    "NO_EMAIL_CONNECTION", 409, null);
        }
        return connected.get(0);
    }

    static String extractAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        int lt = s.indexOf('<');
        int gt = s.indexOf('>');
        if (lt >= 0 && gt > lt) {
            s = s.substring(lt + 1, gt).trim();
        }
        return s.matches("[^@\\s<>\"]+@[^@\\s<>\"]+\\.[^@\\s<>\"]+") ? s.toLowerCase(Locale.ROOT) : null;
    }
}
