package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.CareerMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CareerMetricRepository extends JpaRepository<CareerMetric, UUID> {
    List<CareerMetric> findByCandidateIdAndMetricTypeOrderByRecordedAtAsc(UUID candidateId, String metricType);
    List<CareerMetric> findByCandidateId(UUID candidateId);
}
