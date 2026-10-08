package com.careerpilot.backend.modules.followup.repositories;

import com.careerpilot.backend.modules.followup.domain.EmailSendEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailSendEventRepository extends JpaRepository<EmailSendEvent, UUID> {
    List<EmailSendEvent> findByDraftIdOrderByCreatedAtAsc(UUID draftId);
}
