package com.careerpilot.backend.modules.application.repositories;

import com.careerpilot.backend.modules.application.domain.ApplicationTimelineEvent;
import com.careerpilot.backend.modules.application.domain.TimelineEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationTimelineEventRepository extends JpaRepository<ApplicationTimelineEvent, UUID> {

    Optional<ApplicationTimelineEvent> findByApplicationIdAndCommunicationIdAndEventType(
            UUID applicationId, UUID communicationId, TimelineEventType eventType);

    List<ApplicationTimelineEvent> findByApplicationIdOrderByEventTimestampAscIdAsc(UUID applicationId);
}
