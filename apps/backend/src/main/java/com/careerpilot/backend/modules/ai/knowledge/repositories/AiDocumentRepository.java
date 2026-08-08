package com.careerpilot.backend.modules.ai.knowledge.repositories;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiDocumentRepository extends JpaRepository<AiDocument, UUID> {
    List<AiDocument> findByOwnerId(UUID ownerId);
    Optional<AiDocument> findFirstByOwnerIdAndDocumentTypeOrderByCreatedAtDesc(UUID ownerId, String documentType);
    List<AiDocument> findByOwnerIdAndDocumentType(UUID ownerId, String documentType);
}
