package com.careerpilot.backend.modules.ai.knowledge.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiProviderException;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiChunk;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.domain.EmbeddingReference;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiChunkRepository;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.repositories.EmbeddingReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgePipelineServiceTest {

    private AiDocumentRepository documentRepository;
    private AiChunkRepository chunkRepository;
    private EmbeddingReferenceRepository embeddingReferenceRepository;
    private AiGatewayClient gatewayClient;
    private KnowledgePipelineService pipelineService;
    private UUID documentId;
    private AiDocument doc;

    @BeforeEach
    void setUp() {
        documentRepository = Mockito.mock(AiDocumentRepository.class);
        chunkRepository = Mockito.mock(AiChunkRepository.class);
        embeddingReferenceRepository = Mockito.mock(EmbeddingReferenceRepository.class);
        gatewayClient = Mockito.mock(AiGatewayClient.class);
        pipelineService = new KnowledgePipelineService(documentRepository, chunkRepository,
                embeddingReferenceRepository, gatewayClient, Mockito.mock(ApplicationEventPublisher.class));

        documentId = UUID.randomUUID();
        doc = AiDocument.builder()
                .id(documentId)
                .documentType("RESUME")
                .title("My CV")
                .content("Backend engineer with Java and Spring Boot experience.")
                .structuredMetadata(Map.of("skills", List.of()))
                .status("CREATED")
                .ownerId(UUID.randomUUID())
                .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(AiDocument.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexesRealChunksAndRecordsTheVectorIdsReturnedByTheStore() {
        when(gatewayClient.run(eq("DOCUMENT_INDEX"), anyMap())).thenReturn(Map.of(
                "chunkTexts", List.of("chunk one", "chunk two"),
                "vectorIds", List.of("vec-a", "vec-b"),
                "collection", "cp_resume_ollama_nomic_embed_text_768",
                "dimension", 768,
                "embeddingProvider", "ollama",
                "embeddingModel", "nomic-embed-text"));

        AiDocument processed = pipelineService.processDocument(documentId);

        assertEquals("READY", processed.getStatus());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(gatewayClient).run(eq("DOCUMENT_INDEX"), payload.capture());
        assertEquals(doc.getOwnerId().toString(), payload.getValue().get("ownerId"));
        assertEquals(doc.getContent(), payload.getValue().get("content"));

        ArgumentCaptor<List<EmbeddingReference>> refs = ArgumentCaptor.forClass(List.class);
        verify(embeddingReferenceRepository).saveAll(refs.capture());
        assertEquals(List.of("vec-a", "vec-b"), refs.getValue().stream().map(EmbeddingReference::getVectorId).toList());
        assertTrue(refs.getValue().stream().allMatch(r -> r.getVectorDimension() == 768
                && "nomic-embed-text".equals(r.getEmbeddingModel())));
        ArgumentCaptor<List<AiChunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunks.capture());
        assertEquals(List.of("chunk one", "chunk two"), chunks.getValue().stream().map(AiChunk::getText).toList());
        // Already-parsed documents are not re-parsed.
        verify(gatewayClient, never()).run(eq("RESUME_PARSE"), anyMap());
    }

    @Test
    void documentWithoutContentFailsInsteadOfUsingPlaceholderText() {
        doc.setContent(null);
        assertThrows(IllegalArgumentException.class, () -> pipelineService.processDocument(documentId));
        assertEquals("FAILED", doc.getStatus());
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void privateDocumentWithoutOwnerIsRejected() {
        doc.setOwnerId(null);
        assertThrows(IllegalArgumentException.class, () -> pipelineService.processDocument(documentId));
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void aiFailureMarksDocumentFailedAndPropagates() {
        when(gatewayClient.run(eq("DOCUMENT_INDEX"), anyMap()))
                .thenThrow(new AiProviderException("Embedding provider unreachable", "AI_PROVIDER_UNAVAILABLE", 503));
        AiProviderException ex = assertThrows(AiProviderException.class, () -> pipelineService.processDocument(documentId));
        assertEquals("AI_PROVIDER_UNAVAILABLE", ex.getCode());
        assertEquals("FAILED", doc.getStatus());
    }

    @Test
    void unparsedJobDocumentIsParsedWithItsRealContent() {
        doc.setDocumentType("JOB");
        doc.setOwnerId(null);
        doc.setStructuredMetadata(null);
        when(gatewayClient.run(eq("JOB_PARSE"), anyMap())).thenReturn(Map.of("jobTitle", Map.of("value", "Engineer")));
        when(gatewayClient.run(eq("DOCUMENT_INDEX"), anyMap())).thenReturn(Map.of(
                "chunkTexts", List.of("c"), "vectorIds", List.of("v"), "collection", "cp_job", "dimension", 768,
                "embeddingProvider", "ollama", "embeddingModel", "nomic-embed-text"));

        pipelineService.processDocument(documentId);

        verify(gatewayClient).run(eq("JOB_PARSE"), argThat(p -> doc.getContent().equals(p.get("content"))));
    }
}
