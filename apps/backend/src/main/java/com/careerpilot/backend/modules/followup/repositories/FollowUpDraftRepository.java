package com.careerpilot.backend.modules.followup.repositories;

import com.careerpilot.backend.modules.followup.domain.FollowUpDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpDraftRepository extends JpaRepository<FollowUpDraft, UUID> {
    List<FollowUpDraft> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<FollowUpDraft> findByApplicationIdAndStatus(UUID applicationId, String status);

    Optional<FollowUpDraft> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByApplicationIdAndStatusAndSentAtAfter(UUID applicationId, String status, Instant after);
}
