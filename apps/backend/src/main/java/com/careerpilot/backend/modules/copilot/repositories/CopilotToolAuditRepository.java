package com.careerpilot.backend.modules.copilot.repositories;

import com.careerpilot.backend.modules.copilot.domain.CopilotToolAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CopilotToolAuditRepository extends JpaRepository<CopilotToolAudit, UUID> {
    List<CopilotToolAudit> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
