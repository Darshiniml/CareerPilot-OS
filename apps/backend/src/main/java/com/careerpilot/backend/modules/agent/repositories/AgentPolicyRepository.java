package com.careerpilot.backend.modules.agent.repositories;

import com.careerpilot.backend.modules.agent.domain.AgentPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentPolicyRepository extends JpaRepository<AgentPolicy, UUID> {
    Optional<AgentPolicy> findByUserId(UUID userId);
}
