package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationTimelineEvent;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationTimelineEventRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Unified, deterministic application timeline (Milestone 22.4).
 *
 * <p>Merges the canonical state-transition history ({@code application_state_history}) with
 * communication-derived timeline events ({@code application_timeline_events}) into one
 * chronological view. Ordering is deterministic: authoritative event timestamp ascending, then a
 * stable secondary key (source rank, event type, row id) so equal timestamps never reorder between
 * requests. No timestamps are fabricated — history entries carry their persisted creation time and
 * communication entries carry the communication's received time.</p>
 */
@Service
@RequiredArgsConstructor
public class ApplicationTimelineService {

    private final ApplicationHistoryRepository historyRepository;
    private final ApplicationTimelineEventRepository timelineEventRepository;

    /**
     * Timeline entry. Field names {@code fromState, toState, timestamp, actorId, actorType, reason,
     * durationSeconds} preserve the existing timeline response contract; the remaining fields are
     * additive provenance for communication-derived events.
     */
    @Data
    @Builder
    public static class TimelineEntry {
        private String source;
        private String eventType;
        private String outcome;
        private String fromState;
        private String toState;
        private Instant timestamp;
        private UUID actorId;
        private String actorType;
        private String reason;
        private long durationSeconds;
        private boolean stateChanged;
        private UUID communicationId;
        private UUID timelineEventId;
        private String classification;
        private Double classificationConfidence;
        private String evidence;
    }

    private record Sortable(Instant timestamp, int sourceRank, String eventType, UUID id, TimelineEntry entry) {
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> getTimeline(UUID applicationId) {
        List<Sortable> merged = new ArrayList<>();

        List<ApplicationHistory> histories = historyRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
        for (int i = 0; i < histories.size(); i++) {
            ApplicationHistory h = histories.get(i);
            Instant start = h.getCreatedAt();
            // Deterministic duration: time until the next recorded transition; 0 for the latest
            // (still-open) entry. Never derived from Instant.now(), so repeated reads are identical.
            Instant end = (i + 1 < histories.size()) ? histories.get(i + 1).getCreatedAt() : start;
            long durationSeconds = Math.max(0, Duration.between(start, end).getSeconds());
            merged.add(new Sortable(start, 0, "STATE_TRANSITION", h.getId(), TimelineEntry.builder()
                    .source("STATE_HISTORY")
                    .eventType("STATE_TRANSITION")
                    .outcome("STATE_TRANSITIONED")
                    .fromState(h.getFromState() != null ? h.getFromState().name() : "INITIAL")
                    .toState(h.getToState().name())
                    .timestamp(start)
                    .actorId(h.getActorId())
                    .actorType(h.getActorType() != null ? h.getActorType()
                            : (h.getActorId() != null ? ApplicationHistory.ACTOR_USER : ApplicationHistory.ACTOR_SYSTEM))
                    .communicationId(h.getSourceCommunicationId())
                    .reason(h.getReason())
                    .durationSeconds(durationSeconds)
                    .stateChanged(true)
                    .build()));
        }

        for (ApplicationTimelineEvent event : timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(applicationId)) {
            merged.add(new Sortable(event.getEventTimestamp(), 1, event.getEventType().name(), event.getId(),
                    TimelineEntry.builder()
                            .source("COMMUNICATION")
                            .eventType(event.getEventType().name())
                            .outcome(event.getOutcome().name())
                            .fromState(event.getPreviousState() != null ? event.getPreviousState().name() : null)
                            .toState(event.getNewState() != null ? event.getNewState().name() : null)
                            .timestamp(event.getEventTimestamp())
                            .actorType("COMMUNICATION")
                            .reason(event.getEvidence())
                            .durationSeconds(0)
                            .stateChanged(event.isStateChanged())
                            .communicationId(event.getCommunicationId())
                            .timelineEventId(event.getId())
                            .classification(event.getClassification() != null ? event.getClassification().name() : null)
                            .classificationConfidence(event.getClassificationConfidence())
                            .evidence(event.getEvidence())
                            .build()));
        }

        merged.sort(Comparator
                .comparing(Sortable::timestamp)
                .thenComparingInt(Sortable::sourceRank)
                .thenComparing(Sortable::eventType)
                .thenComparing(s -> s.id().toString()));
        return merged.stream().map(Sortable::entry).toList();
    }
}
