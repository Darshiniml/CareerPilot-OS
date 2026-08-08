package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.CareerGoalProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareerGoalProgressRepository extends JpaRepository<CareerGoalProgress, UUID> {
    Optional<CareerGoalProgress> findByGoalId(UUID goalId);
}
