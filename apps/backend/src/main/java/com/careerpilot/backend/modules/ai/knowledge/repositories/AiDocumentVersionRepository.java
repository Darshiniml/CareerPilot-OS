package com.careerpilot.backend.modules.ai.knowledge.repositories;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AiDocumentVersionRepository extends JpaRepository<AiDocumentVersion, UUID> {
    List<AiDocumentVersion> findByParentDocumentIdOrderByVersionNumberDesc(UUID documentId);
}
