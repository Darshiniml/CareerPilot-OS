package com.careerpilot.backend.modules.ai.events;

import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.backend.modules.ai.matching.MatchingCache;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.shared.events.PreferencesUpdatedEvent;
import com.careerpilot.shared.events.ResumeDeletedEvent;
import com.careerpilot.shared.events.ResumeUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Keeps AI-derived state consistent with candidate data changes.
 *
 * <ul>
 *   <li>Resume uploaded / preferences updated: cached match results for the candidate are evicted
 *       (resume processing itself is done by {@code ResumeProcessingService}).</li>
 *   <li>Resume deleted: the resume's vectors are removed from the vector store and its AI documents
 *       archived, so deleted content can no longer be retrieved.</li>
 * </ul>
 */
@Component
@Slf4j
public class AiEventConsumer {

    private final ResumeVersionRepository versionRepository;
    private final AiDocumentRepository documentRepository;
    private final KnowledgePipelineService pipelineService;
    private final MatchingCache matchingCache;

    public AiEventConsumer(ResumeVersionRepository versionRepository,
                           AiDocumentRepository documentRepository,
                           KnowledgePipelineService pipelineService,
                           MatchingCache matchingCache) {
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.pipelineService = pipelineService;
        this.matchingCache = matchingCache;
    }

    @EventListener
    public void handleResumeUploaded(ResumeUploadedEvent event) {
        matchingCache.evictCandidate(event.getUserId());
    }

    @Async
    @EventListener
    public void handleResumeDeleted(ResumeDeletedEvent event) {
        matchingCache.evictCandidate(event.getUserId());
        for (ResumeVersion version : versionRepository.findByResumeIdOrderByVersionNumberDesc(event.getResumeId())) {
            documentRepository.findById(version.getId())
                    .filter(doc -> event.getUserId().equals(doc.getOwnerId()))
                    .ifPresent(this::archive);
        }
    }

    @EventListener
    public void handlePreferencesUpdated(PreferencesUpdatedEvent event) {
        matchingCache.evictCandidate(event.getUserId());
    }

    private void archive(AiDocument doc) {
        try {
            pipelineService.removeFromIndex(doc);
        } catch (AiServiceException e) {
            log.warn("Could not remove vectors of document {} ({}); it is archived and excluded from use", doc.getId(), e.getCode());
        }
        doc.setStatus("ARCHIVED");
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
    }
}
