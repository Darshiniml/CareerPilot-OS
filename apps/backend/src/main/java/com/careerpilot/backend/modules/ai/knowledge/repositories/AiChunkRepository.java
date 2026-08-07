package com.careerpilot.backend.modules.ai.knowledge.repositories;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiChunkRepository extends JpaRepository<AiChunk, UUID> {
    List<AiChunk> findByDocumentId(UUID documentId);
}
