package com.careerpilot.backend.modules.followup.repositories;

import com.careerpilot.backend.modules.followup.domain.FollowUpDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpDecisionRepository extends JpaRepository<FollowUpDecision, UUID> {
    List<FollowUpDecision> findByUserId(UUID userId);

    Optional<FollowUpDecision> findByUserIdAndRecommendationKey(UUID userId, String recommendationKey);
}
