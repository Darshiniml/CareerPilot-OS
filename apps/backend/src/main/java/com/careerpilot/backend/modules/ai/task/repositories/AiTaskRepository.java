package com.careerpilot.backend.modules.ai.task.repositories;

import com.careerpilot.backend.modules.ai.task.domain.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {
    List<AiTask> findByWorkflowId(UUID workflowId);
}
