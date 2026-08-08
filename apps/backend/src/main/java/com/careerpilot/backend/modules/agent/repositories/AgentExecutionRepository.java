package com.careerpilot.backend.modules.agent.repositories;

import com.careerpilot.backend.modules.agent.domain.AgentExecution;
import com.careerpilot.backend.modules.agent.domain.AgentExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {
    List<AgentExecution> findByTaskId(UUID taskId);
    List<AgentExecution> findByStatus(AgentExecutionStatus status);
}
