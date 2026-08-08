package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.CareerGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CareerGoalRepository extends JpaRepository<CareerGoal, UUID> {
    List<CareerGoal> findByCandidateId(UUID candidateId);
}
