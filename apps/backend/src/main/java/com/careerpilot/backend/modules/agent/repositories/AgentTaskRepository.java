package com.careerpilot.backend.modules.agent.repositories;

import com.careerpilot.backend.modules.agent.domain.AgentTask;
import com.careerpilot.backend.modules.agent.domain.AgentTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {
    List<AgentTask> findByWorkflowId(UUID workflowId);
    List<AgentTask> findByWorkflowIdOrderByPriorityDescCreatedAtAsc(UUID workflowId);
    List<AgentTask> findByStatus(AgentTaskStatus status);
}
