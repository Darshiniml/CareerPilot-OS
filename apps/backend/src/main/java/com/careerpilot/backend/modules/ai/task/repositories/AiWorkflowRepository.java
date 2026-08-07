package com.careerpilot.backend.modules.ai.task.repositories;

import com.careerpilot.backend.modules.ai.task.domain.AiWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AiWorkflowRepository extends JpaRepository<AiWorkflow, UUID> {
}
