package com.careerpilot.backend.modules.followup.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.followup.domain.EmailSendEvent;
import com.careerpilot.backend.modules.followup.domain.FollowUpDraft;
import com.careerpilot.backend.modules.followup.repositories.EmailSendEventRepository;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDraftRepository;
import com.careerpilot.backend.modules.profile.services.ProfileService;
import com.careerpilot.shared.dto.profile.ProfileDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * M22.6: AI follow-up drafts built only from stored facts (application, job, profile, the related
 * email). Drafts are never sent automatically; approval (M22.7) is a separate explicit user action.
 */
@Service
@RequiredArgsConstructor
public class FollowUpDraftService {

    public static final Set<String> DRAFT_TYPES = Set.of("APPLICATION_FOLLOW_UP", "INTERVIEW_THANK_YOU",
            "RECRUITER_RESPONSE", "ADDITIONAL_INFORMATION_RESPONSE", "INTERVIEW_RESCHEDULE_RESPONSE", "OFFER_RESPONSE");
    static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\]]{2,60}\\]");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FollowUpDraftRepository draftRepository;
    private final EmailSendEventRepository eventRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final DiscoveryJobRepository jobRepository;
    private final HrCommunicationRepository communicationRepository;
    private final ProfileService profileService;
    private final AiGatewayClient gatewayClient;
    private final Clock clock;

    public record CreateDraftCommand(UUID applicationId, String draftType, UUID communicationId,
                                     String recommendationKey, String userInstructions, LocalDate interviewCompletedOn) {
    }

    // Not transactional: the model call must not pin a DB connection; the draft is saved in one write.
    @SuppressWarnings("unchecked")
    public FollowUpDraft create(UUID userId, CreateDraftCommand cmd) {
        if (cmd.draftType() == null || !DRAFT_TYPES.contains(cmd.draftType())) {
            throw new IllegalArgumentException("draftType must be one of " + new TreeSet<>(DRAFT_TYPES));
        }
        ApplicationRecord app = applicationRepository.findById(cmd.applicationId())
                .filter(a -> userId.equals(a.getCandidateId()))
                .orElseThrow(() -> new NoSuchElementException("Application not found"));
        DiscoveryJob job = jobRepository.findById(app.getJobId())
                .orElseThrow(() -> new IllegalStateException("The job for this application is no longer available"));
        if (job.getCompany() == null || job.getTitle() == null) {
            throw new IllegalStateException("The job record has no company/title; a factual draft is not possible");
        }
        HrCommunication email = null;
        if (cmd.communicationId() != null) {
            email = communicationRepository.findByIdAndCandidateId(cmd.communicationId(), userId)
                    .filter(c -> app.getApplicationId().equals(c.getMatchedApplicationId()))
                    .orElseThrow(() -> new NoSuchElementException("Email not found for this application"));
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("company", job.getCompany());
        context.put("jobTitle", job.getTitle());
        context.put("applicationState", app.getWorkflowState().name());
        if (app.getSubmittedAt() != null) {
            context.put("appliedAt", app.getSubmittedAt().toString().substring(0, 10));
        }
        ProfileDto profile = profileService.getProfile(userId);
        if (profile != null) {
            context.put("candidateFirstName", profile.getFirstName());
            context.put("candidateLastName", profile.getLastName());
        }
        boolean offer = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId).stream()
                .anyMatch(c -> app.getApplicationId().equals(c.getMatchedApplicationId())
                        && c.getClassification() == CommunicationClassification.OFFER);
        if (offer) {
            context.put("offerReceived", true);
        }
        if (cmd.interviewCompletedOn() != null) {
            if (cmd.interviewCompletedOn().isAfter(LocalDate.now(clock))) {
                throw new IllegalArgumentException("interviewCompletedOn cannot be in the future");
            }
            context.put("interview", Map.of("completedOn", cmd.interviewCompletedOn().toString(),
                    "source", "confirmed by the candidate"));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("draftType", cmd.draftType());
        payload.put("context", context);
        if (email != null) {
            payload.put("relatedEmail", "From: " + email.getSender() + "\nSubject: " + email.getSubject() + "\n\n"
                    + truncate(email.getBody(), 8000));
        }
        if (cmd.userInstructions() != null && !cmd.userInstructions().isBlank()) {
            payload.put("userInstructions", truncate(cmd.userInstructions(), 1000));
        }
        Map<String, Object> result = gatewayClient.run("FOLLOW_UP_DRAFT", payload);

        FollowUpDraft draft = FollowUpDraft.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .applicationId(app.getApplicationId())
                .communicationId(email != null ? email.getId() : null)
                .recommendationKey(cmd.recommendationKey())
                .draftType(cmd.draftType())
                .subject(truncate(Objects.toString(result.get("subject"), ""), 500))
                .body(Objects.toString(result.get("body"), ""))
                .placeholdersJson(toJson(result.getOrDefault("placeholders", List.of())))
                .verificationJson(toJson(result.get("verification")))
                .status(FollowUpDraft.DRAFT)
                .recipient(email != null ? email.getSender() : null)
                .createdAt(clock.instant())
                .updatedAt(clock.instant())
                .build();
        if (draft.getSubject().isBlank() || draft.getBody().isBlank()) {
            throw new IllegalStateException("The AI returned an empty draft; please try again");
        }
        return draftRepository.save(draft);
    }

    @Transactional
    public FollowUpDraft update(UUID userId, UUID draftId, String subject, String body, String recipient) {
        FollowUpDraft draft = owned(userId, draftId);
        if (!Set.of(FollowUpDraft.DRAFT, FollowUpDraft.APPROVED, FollowUpDraft.FAILED).contains(draft.getStatus())) {
            throw new IllegalStateException("A " + draft.getStatus() + " draft cannot be edited");
        }
        if (subject != null) {
            draft.setSubject(truncate(subject.trim(), 500));
        }
        if (body != null) {
            draft.setBody(body);
        }
        if (recipient != null) {
            draft.setRecipient(recipient.isBlank() ? null : recipient.trim());
        }
        draft.setPlaceholdersJson(toJson(placeholders(draft)));
        draft.setEditedByUser(true);
        // Any change requires a fresh approval of the exact text that will be sent.
        draft.setStatus(FollowUpDraft.DRAFT);
        draft.setApprovedAt(null);
        draft.setUpdatedAt(clock.instant());
        return draftRepository.save(draft);
    }

    @Transactional
    public FollowUpDraft discard(UUID userId, UUID draftId) {
        FollowUpDraft draft = owned(userId, draftId);
        if (FollowUpDraft.SENT.equals(draft.getStatus()) || FollowUpDraft.SENDING.equals(draft.getStatus())) {
            throw new IllegalStateException("A sent draft cannot be discarded");
        }
        draft.setStatus(FollowUpDraft.DISCARDED);
        draft.setUpdatedAt(clock.instant());
        event(draft, "DISCARDED", null, null);
        return draftRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public List<FollowUpDraft> list(UUID userId) {
        return draftRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public FollowUpDraft get(UUID userId, UUID draftId) {
        return owned(userId, draftId);
    }

    public FollowUpDraft owned(UUID userId, UUID draftId) {
        return draftRepository.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new NoSuchElementException("Draft not found"));
    }

    public static List<String> placeholders(FollowUpDraft draft) {
        List<String> found = new ArrayList<>();
        var m = PLACEHOLDER.matcher(draft.getSubject() + "\n" + draft.getBody());
        while (m.find()) {
            if (!found.contains(m.group())) {
                found.add(m.group());
            }
        }
        return found;
    }

    public void event(FollowUpDraft draft, String type, String provider, String detail) {
        eventRepository.save(EmailSendEvent.builder()
                .id(UUID.randomUUID())
                .userId(draft.getUserId())
                .draftId(draft.getId())
                .eventType(type)
                .provider(provider)
                .recipient(draft.getRecipient())
                .detail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail)
                .correlationId(MDC.get("correlationId"))
                .createdAt(clock.instant())
                .build());
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static String toJson(Object o) {
        try {
            return o == null ? null : MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
