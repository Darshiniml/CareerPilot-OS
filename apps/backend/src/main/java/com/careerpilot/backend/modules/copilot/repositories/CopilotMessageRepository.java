package com.careerpilot.backend.modules.copilot.repositories;

import com.careerpilot.backend.modules.copilot.domain.CopilotMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CopilotMessageRepository extends JpaRepository<CopilotMessage, UUID> {

    List<CopilotMessage> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Transactional
    long deleteByUserId(UUID userId);
}
