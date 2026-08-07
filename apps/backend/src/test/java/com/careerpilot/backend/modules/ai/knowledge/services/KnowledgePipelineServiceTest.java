package com.careerpilot.backend.modules.ai.knowledge.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.*;
import com.careerpilot.backend.modules.ai.knowledge.repositories.*;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KnowledgePipelineServiceTest {

    private AiDocumentRepository documentRepository;
    private AiChunkRepository chunkRepository;
    private EmbeddingReferenceRepository embeddingReferenceRepository;
    private AiGatewayClient gatewayClient;
    private ApplicationEventPublisher eventPublisher;

    private KnowledgePipelineService pipelineService;
    private UUID documentId;
    private AiDocument mockDoc;

    @BeforeEach
    void setUp() {
        documentRepository = Mockito.mock(AiDocumentRepository.class);
        chunkRepository = Mockito.mock(AiChunkRepository.class);
        embeddingReferenceRepository = Mockito.mock(EmbeddingReferenceRepository.class);
        gatewayClient = Mockito.mock(AiGatewayClient.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        pipelineService = new KnowledgePipelineService(
                documentRepository,
                chunkRepository,
                embeddingReferenceRepository,
                gatewayClient,
                eventPublisher
        );

        documentId = UUID.randomUUID();
        mockDoc = AiDocument.builder()
                .id(documentId)
                .documentType("RESUME")
                .title("My CV")
                .content("Mocked resume text content for parser split processing")
                .status("CREATED")
                .ownerId(UUID.randomUUID())
                .build();

        Mockito.when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDoc));
        Mockito.when(documentRepository.save(any(AiDocument.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    void testProcessDocument_LifecycleCompletesSuccessfullyToReady() {
        // Setup gateway mock return value
        AiTaskResponseDto mockResponse = AiTaskResponseDto.builder()
                .taskId(UUID.randomUUID())
                .status("COMPLETED")
                .provider("mock")
                .result(new HashMap<>())
                .metadata(new HashMap<>())
                .build();

        Mockito.when(gatewayClient.executeTask(any())).thenReturn(mockResponse);

        AiDocument processed = pipelineService.processDocument(documentId);

        assertEquals("READY", processed.getStatus());
        verify(documentRepository, times(7)).save(any(AiDocument.class)); // Saved at each stage transition
        verify(chunkRepository, times(1)).saveAll(any());
        verify(embeddingReferenceRepository, times(1)).save(any());
    }
}
