package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.CareerAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareerAnalyticsRepository extends JpaRepository<CareerAnalytics, UUID> {
    Optional<CareerAnalytics> findByCandidateIdAndAnalysisPeriod(UUID candidateId, String analysisPeriod);
    List<CareerAnalytics> findByCandidateId(UUID candidateId);
}
