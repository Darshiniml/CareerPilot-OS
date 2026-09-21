package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.communication.domain.FollowUpRecommendation;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;

import java.util.Optional;

/**
 * Foundation abstraction for follow-up recommendation. No production implementation is
 * registered in this milestone; the AI-driven recommender is deferred to a later stage.
 */
public interface FollowUpRecommender {

    Optional<FollowUpRecommendation> recommend(HrCommunication communication);
}
