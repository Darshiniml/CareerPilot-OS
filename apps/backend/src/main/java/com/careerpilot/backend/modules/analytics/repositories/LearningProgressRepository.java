package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningProgressRepository extends JpaRepository<LearningProgress, UUID> {
    List<LearningProgress> findByCandidateId(UUID candidateId);
    Optional<LearningProgress> findByCandidateIdAndSkill(UUID candidateId, String skill);
}
