package com.careerpilot.backend.modules.application.repositories;

import com.careerpilot.backend.modules.application.domain.ApplicationDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationDecisionRepository extends JpaRepository<ApplicationDecision, UUID> {
    Optional<ApplicationDecision> findByApplicationId(UUID applicationId);
    Optional<ApplicationDecision> findByCandidateIdAndJobId(UUID candidateId, UUID jobId);
}
