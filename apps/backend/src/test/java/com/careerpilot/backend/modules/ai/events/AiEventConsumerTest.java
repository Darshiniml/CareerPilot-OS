package com.careerpilot.backend.modules.ai.events;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.backend.modules.ai.matching.MatchingCache;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import com.careerpilot.shared.events.ResumeDeletedEvent;
import com.careerpilot.shared.events.ResumeUploadedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiEventConsumerTest {

    private ResumeVersionRepository versionRepository;
    private AiDocumentRepository documentRepository;
    private KnowledgePipelineService pipelineService;
    private MatchingCache matchingCache;
    private AiEventConsumer eventConsumer;

    @BeforeEach
    void setUp() {
        versionRepository = Mockito.mock(ResumeVersionRepository.class);
        documentRepository = Mockito.mock(AiDocumentRepository.class);
        pipelineService = Mockito.mock(KnowledgePipelineService.class);
        matchingCache = new MatchingCache(null);
        eventConsumer = new AiEventConsumer(versionRepository, documentRepository, pipelineService, matchingCache);
    }

    @Test
    void resumeUploadEvictsOnlyThatCandidatesCachedMatches() {
        UUID candidate = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        matchingCache.put(MatchingCache.generateCacheKey(candidate, UUID.randomUUID()), new MatchResultDto());
        matchingCache.put(MatchingCache.generateCacheKey(other, UUID.randomUUID()), new MatchResultDto());

        eventConsumer.handleResumeUploaded(ResumeUploadedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now()).userId(candidate).resumeId(UUID.randomUUID()).build());

        assertEquals(1, matchingCache.size());
    }

    @Test
    void resumeDeletionRemovesOwnedVectorsAndArchivesDocuments() {
        UUID owner = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();
        ResumeVersion version = ResumeVersion.builder().id(UUID.randomUUID()).versionNumber(1).build();
        AiDocument doc = AiDocument.builder().id(version.getId()).ownerId(owner).documentType("RESUME").title("cv").build();
        when(versionRepository.findByResumeIdOrderByVersionNumberDesc(resumeId)).thenReturn(List.of(version));
        when(documentRepository.findById(version.getId())).thenReturn(Optional.of(doc));

        eventConsumer.handleResumeDeleted(ResumeDeletedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now()).userId(owner).resumeId(resumeId).build());

        verify(pipelineService).removeFromIndex(doc);
        verify(documentRepository).save(any(AiDocument.class));
        assertEquals("ARCHIVED", doc.getStatus());
    }

    @Test
    void resumeDeletionNeverTouchesAnotherUsersDocument() {
        UUID resumeId = UUID.randomUUID();
        ResumeVersion version = ResumeVersion.builder().id(UUID.randomUUID()).versionNumber(1).build();
        AiDocument foreign = AiDocument.builder().id(version.getId()).ownerId(UUID.randomUUID()).documentType("RESUME").title("x").build();
        when(versionRepository.findByResumeIdOrderByVersionNumberDesc(resumeId)).thenReturn(List.of(version));
        when(documentRepository.findById(version.getId())).thenReturn(Optional.of(foreign));

        eventConsumer.handleResumeDeleted(ResumeDeletedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now()).userId(UUID.randomUUID()).resumeId(resumeId).build());

        verifyNoInteractions(pipelineService);
        verify(documentRepository, never()).save(any());
    }
}
