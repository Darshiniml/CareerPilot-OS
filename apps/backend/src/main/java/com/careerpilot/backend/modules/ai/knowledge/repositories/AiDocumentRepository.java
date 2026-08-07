package com.careerpilot.backend.modules.ai.knowledge.repositories;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiDocumentRepository extends JpaRepository<AiDocument, UUID> {
    List<AiDocument> findByOwnerId(UUID ownerId);
}
