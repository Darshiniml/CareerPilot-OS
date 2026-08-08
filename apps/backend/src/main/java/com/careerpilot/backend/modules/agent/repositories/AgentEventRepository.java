package com.careerpilot.backend.modules.agent.repositories;

import com.careerpilot.backend.modules.agent.domain.AgentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentEventRepository extends JpaRepository<AgentEvent, UUID> {
    List<AgentEvent> findByWorkflowId(UUID workflowId);
    List<AgentEvent> findByCorrelationId(String correlationId);
}
