package com.careerpilot.backend.modules.ai.knowledge.repositories;

import com.careerpilot.backend.modules.ai.knowledge.domain.EmbeddingReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmbeddingReferenceRepository extends JpaRepository<EmbeddingReference, UUID> {
    List<EmbeddingReference> findByDocumentId(UUID documentId);
}
