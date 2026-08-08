package com.careerpilot.backend.modules.agent.repositories;

import com.careerpilot.backend.modules.agent.domain.AgentWorkflow;
import com.careerpilot.backend.modules.agent.domain.AgentWorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflow, UUID> {
    List<AgentWorkflow> findByUserId(UUID userId);
    List<AgentWorkflow> findByUserIdAndStatus(UUID userId, AgentWorkflowStatus status);
}
