package com.careerpilot.backend.modules.followup.services;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.services.ApplicationTrackingService;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.copilot.services.FollowUpToolBridge;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.followup.domain.FollowUpDecision;
import com.careerpilot.backend.modules.followup.domain.FollowUpDraft;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDecisionRepository;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * M22.5: deterministic follow-up recommendations.
 *
 * <p>Rules (all evidence comes from stored applications, state history and classified HR emails):</p>
 * <ul>
 *   <li>REPLY_INTERVIEW_INVITATION — an interview invitation has not been answered (HIGH, today).</li>
 *   <li>REPLY_RESCHEDULE — an interview was rescheduled (HIGH, today).</li>
 *   <li>PROVIDE_REQUESTED_INFORMATION — the recruiter asked for information (HIGH, today).</li>
 *   <li>RESPOND_TO_OFFER — an offer was received (HIGH, within 2 days of receipt).</li>
 *   <li>NO_RESPONSE_AFTER_APPLICATION — submitted ≥ 7 days ago with no recruiter email since
 *       (MEDIUM; HIGH from 14 days; LOW after 45 days as the posting is likely stale).</li>
 *   <li>ASSESSMENT_STATUS_CHECK — in ASSESSMENT ≥ 7 days with no email since (MEDIUM).</li>
 *   <li>POST_INTERVIEW_STATUS_CHECK — in INTERVIEW ≥ 10 days with no email since (MEDIUM).</li>
 * </ul>
 * <p>No recommendation is made for terminal applications, when a follow-up was sent for the same
 * application within the last 7 days, or when the user dismissed/snoozed it. Recommendations never
 * name a recruiter or contact address unless it comes from a stored email.</p>
 */
@Service
@RequiredArgsConstructor
public class FollowUpRecommendationService implements FollowUpToolBridge {

    static final int NO_RESPONSE_DAYS = 7;
    static final int NO_RESPONSE_HIGH_DAYS = 14;
    static final int STALE_DAYS = 45;
    static final int ASSESSMENT_DAYS = 7;
    static final int POST_INTERVIEW_DAYS = 10;
    static final int RECENT_FOLLOW_UP_DAYS = 7;

    private static final Set<WorkflowState> AWAITING_RESPONSE = EnumSet.of(WorkflowState.SUBMITTED,
            WorkflowState.SUBMITTED_VERIFIED, WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMISSION_UNVERIFIED,
            WorkflowState.UNDER_REVIEW);

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final HrCommunicationRepository communicationRepository;
    private final DiscoveryJobRepository jobRepository;
    private final FollowUpDecisionRepository decisionRepository;
    private final FollowUpDraftRepository draftRepository;
    private final Clock clock;

    public record Recommendation(String key, UUID applicationId, String jobTitle, String company, String ruleCode,
                                 String reason, String urgency, LocalDate recommendedDate, String recommendedChannel,
                                 List<String> evidence, UUID relatedCommunicationId, String suggestedDraftType,
                                 String suggestedRecipient, double confidence) {
    }

    @Override
    public Object recommendations(UUID userId) {
        return recommend(userId);
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommend(UUID userId) {
        Instant now = clock.instant();
        Map<String, FollowUpDecision> decisions = new HashMap<>();
        decisionRepository.findByUserId(userId).forEach(d -> decisions.put(d.getRecommendationKey(), d));
        List<HrCommunication> communications = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId);

        List<Recommendation> result = new ArrayList<>();
        for (ApplicationRecord app : applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)) {
            WorkflowState state = app.getWorkflowState();
            if (state == null || ApplicationTrackingService.TERMINAL_STATES.contains(state)) {
                continue;
            }
            if (draftRepository.existsByApplicationIdAndStatusAndSentAtAfter(app.getApplicationId(), FollowUpDraft.SENT,
                    now.minus(RECENT_FOLLOW_UP_DAYS, ChronoUnit.DAYS))) {
                continue; // the candidate already followed up recently
            }
            List<HrCommunication> appEmails = communications.stream()
                    .filter(c -> app.getApplicationId().equals(c.getMatchedApplicationId()))
                    .filter(c -> c.getProcessingStatus() == CommunicationProcessingStatus.PROCESSED)
                    .toList(); // newest first
            Optional<DiscoveryJob> job = jobRepository.findById(app.getJobId());
            String title = job.map(DiscoveryJob::getTitle).orElse(null);
            String company = job.map(DiscoveryJob::getCompany).orElse(null);

            Optional<Recommendation> rec = emailDriven(app, appEmails, title, company, now)
                    .or(() -> timeDriven(app, appEmails, title, company, now));
            rec.filter(r -> notSuppressed(r, decisions.get(r.key()), now)).ifPresent(result::add);
        }
        result.sort(Comparator.comparing((Recommendation r) -> urgencyRank(r.urgency()))
                .thenComparing(Recommendation::recommendedDate));
        return result;
    }

    @Transactional
    public void decide(UUID userId, String key, String decision, Integer snoozeDays) {
        Recommendation rec = recommend(userId).stream().filter(r -> r.key().equals(key)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Recommendation not found"));
        if (!Set.of(FollowUpDecision.DISMISSED, FollowUpDecision.SNOOZED, FollowUpDecision.DONE).contains(decision)) {
            throw new IllegalArgumentException("decision must be DISMISSED, SNOOZED or DONE");
        }
        FollowUpDecision d = decisionRepository.findByUserIdAndRecommendationKey(userId, key)
                .orElseGet(() -> FollowUpDecision.builder().id(UUID.randomUUID()).userId(userId)
                        .recommendationKey(key).applicationId(rec.applicationId()).build());
        d.setDecision(decision);
        d.setSnoozeUntil(FollowUpDecision.SNOOZED.equals(decision)
                ? clock.instant().plus(Math.max(1, Math.min(snoozeDays == null ? 3 : snoozeDays, 30)), ChronoUnit.DAYS) : null);
        d.setCreatedAt(clock.instant());
        decisionRepository.save(d);
    }

    // ------------------------------------------------------------------ rules

    private Optional<Recommendation> emailDriven(ApplicationRecord app, List<HrCommunication> emails,
                                                 String title, String company, Instant now) {
        if (emails.isEmpty()) {
            return Optional.empty();
        }
        HrCommunication latest = emails.get(0);
        if (latest.getClassificationConfidence() == null || latest.getClassificationConfidence() < 0.5) {
            return Optional.empty(); // uncertain classification never drives a recommendation
        }
        CommunicationClassification c = latest.getClassification();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<String> evidence = new ArrayList<>();
        evidence.add("Email \"" + truncate(latest.getSubject()) + "\" from " + latest.getSender()
                + " received " + latest.getReceivedAt() + " classified " + c
                + " (confidence " + latest.getClassificationConfidence() + ")");
        String recipient = latest.getSender();
        return switch (c) {
            case INTERVIEW_INVITATION -> Optional.of(rec(app, title, company, "REPLY_INTERVIEW_INVITATION",
                    "The recruiter invited you to interview; reply with your availability.", "HIGH", today, "EMAIL",
                    evidence, latest.getId(), "RECRUITER_RESPONSE", recipient, 0.9));
            case INTERVIEW_RESCHEDULED -> Optional.of(rec(app, title, company, "REPLY_RESCHEDULE",
                    "Your interview was rescheduled; confirm the new time.", "HIGH", today, "EMAIL",
                    evidence, latest.getId(), "INTERVIEW_RESCHEDULE_RESPONSE", recipient, 0.9));
            case ADDITIONAL_INFORMATION_REQUESTED -> Optional.of(rec(app, title, company, "PROVIDE_REQUESTED_INFORMATION",
                    "The recruiter asked for additional information.", "HIGH", today, "EMAIL",
                    evidence, latest.getId(), "ADDITIONAL_INFORMATION_RESPONSE", recipient, 0.9));
            case OFFER -> Optional.of(rec(app, title, company, "RESPOND_TO_OFFER",
                    "You received an offer; acknowledge it and ask any open questions.", "HIGH",
                    LocalDate.ofInstant(latest.getReceivedAt().plus(2, ChronoUnit.DAYS), ZoneOffset.UTC), "EMAIL",
                    evidence, latest.getId(), "OFFER_RESPONSE", recipient, 0.9));
            default -> Optional.empty();
        };
    }

    private Optional<Recommendation> timeDriven(ApplicationRecord app, List<HrCommunication> emails,
                                                String title, String company, Instant now) {
        WorkflowState state = app.getWorkflowState();
        Instant lastEmail = emails.isEmpty() ? null : emails.get(0).getReceivedAt();
        String recipient = emails.isEmpty() ? null : emails.get(0).getSender();
        String channel = recipient != null ? "EMAIL" : "APPLICATION_PORTAL";
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        if (AWAITING_RESPONSE.contains(state)) {
            Instant since = submittedAt(app);
            if (since == null) {
                return Optional.empty();
            }
            Instant reference = lastEmail != null && lastEmail.isAfter(since) ? lastEmail : since;
            long days = ChronoUnit.DAYS.between(reference, now);
            if (days < NO_RESPONSE_DAYS) {
                return Optional.empty();
            }
            String urgency = days >= STALE_DAYS ? "LOW" : days >= NO_RESPONSE_HIGH_DAYS ? "HIGH" : "MEDIUM";
            List<String> evidence = new ArrayList<>(List.of("Submitted " + since, days + " days without a recruiter email"));
            if (days >= STALE_DAYS) {
                evidence.add("Older than " + STALE_DAYS + " days: the role may be filled; one polite follow-up at most");
            }
            return Optional.of(rec(app, title, company, "NO_RESPONSE_AFTER_APPLICATION",
                    "No response " + days + " days after applying.", urgency, today, channel, evidence,
                    emails.isEmpty() ? null : emails.get(0).getId(), "APPLICATION_FOLLOW_UP", recipient,
                    days >= STALE_DAYS ? 0.5 : 0.75));
        }
        if (state == WorkflowState.ASSESSMENT || state == WorkflowState.INTERVIEW) {
            Instant entered = enteredState(app, state);
            if (entered == null) {
                return Optional.empty();
            }
            Instant reference = lastEmail != null && lastEmail.isAfter(entered) ? lastEmail : entered;
            long days = ChronoUnit.DAYS.between(reference, now);
            int threshold = state == WorkflowState.ASSESSMENT ? ASSESSMENT_DAYS : POST_INTERVIEW_DAYS;
            if (days < threshold) {
                return Optional.empty();
            }
            String rule = state == WorkflowState.ASSESSMENT ? "ASSESSMENT_STATUS_CHECK" : "POST_INTERVIEW_STATUS_CHECK";
            String reason = state == WorkflowState.ASSESSMENT
                    ? "No update " + days + " days after the assessment stage began."
                    : "No update " + days + " days after reaching the interview stage.";
            return Optional.of(rec(app, title, company, rule, reason, "MEDIUM", today, channel,
                    List.of("Entered " + state + " at " + entered, days + " days without a recruiter email"),
                    emails.isEmpty() ? null : emails.get(0).getId(), "APPLICATION_FOLLOW_UP", recipient, 0.7));
        }
        return Optional.empty();
    }

    private Instant submittedAt(ApplicationRecord app) {
        if (app.getSubmittedAt() != null) {
            return app.getSubmittedAt();
        }
        return historyRepository.findByApplicationIdOrderByCreatedAtAsc(app.getApplicationId()).stream()
                .filter(h -> h.getToState() == WorkflowState.SUBMITTED || h.getToState() == WorkflowState.SUBMITTED_VERIFIED)
                .map(ApplicationHistory::getCreatedAt).findFirst().orElse(null);
    }

    private Instant enteredState(ApplicationRecord app, WorkflowState state) {
        List<ApplicationHistory> history = historyRepository.findByApplicationIdOrderByCreatedAtAsc(app.getApplicationId());
        Instant entered = null;
        for (ApplicationHistory h : history) {
            if (h.getToState() == state) {
                entered = h.getCreatedAt();
            }
        }
        return entered;
    }

    private static boolean notSuppressed(Recommendation r, FollowUpDecision d, Instant now) {
        if (d == null) {
            return true;
        }
        if (FollowUpDecision.SNOOZED.equals(d.getDecision())) {
            return d.getSnoozeUntil() != null && d.getSnoozeUntil().isBefore(now);
        }
        return false; // dismissed or done
    }

    private static Recommendation rec(ApplicationRecord app, String title, String company, String rule, String reason,
                                      String urgency, LocalDate date, String channel, List<String> evidence,
                                      UUID communicationId, String draftType, String recipient, double confidence) {
        // Stable key: the same situation keeps the same key, a new email produces a new one.
        String anchor = communicationId != null ? communicationId.toString() : "-";
        String key = app.getApplicationId() + ":" + rule + ":" + anchor;
        return new Recommendation(key, app.getApplicationId(), title, company, rule, reason, urgency, date, channel,
                List.copyOf(evidence), communicationId, draftType, recipient, confidence);
    }

    private static int urgencyRank(String urgency) {
        return switch (urgency) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
