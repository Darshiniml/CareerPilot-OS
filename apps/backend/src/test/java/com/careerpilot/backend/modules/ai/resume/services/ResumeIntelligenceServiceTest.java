package com.careerpilot.backend.modules.ai.resume.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeValidationReport;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeValidationReportRepository;
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

class ResumeIntelligenceServiceTest {

    private AiDocumentRepository documentRepository;
    private ResumeIntelligenceCacheRepository cacheRepository;
    private ResumeValidationReportRepository validationReportRepository;
    private KnowledgePipelineService pipelineService;
    private AiGatewayClient gatewayClient;
    private ApplicationEventPublisher eventPublisher;

    private ResumeIntelligenceService resumeIntelligenceService;
    private UUID documentId;
    private AiDocument mockDoc;

    @BeforeEach
    void setUp() {
        documentRepository = Mockito.mock(AiDocumentRepository.class);
        cacheRepository = Mockito.mock(ResumeIntelligenceCacheRepository.class);
        validationReportRepository = Mockito.mock(ResumeValidationReportRepository.class);
        pipelineService = Mockito.mock(KnowledgePipelineService.class);
        gatewayClient = Mockito.mock(AiGatewayClient.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        resumeIntelligenceService = new ResumeIntelligenceService(
                documentRepository,
                cacheRepository,
                validationReportRepository,
                pipelineService,
                gatewayClient,
                eventPublisher
        );

        documentId = UUID.randomUUID();
        mockDoc = AiDocument.builder()
                .id(documentId)
                .documentType("RESUME")
                .title("My CV")
                .content("Jane Doe\njane.doe@example.com\nGoogle\nSoftware Engineer")
                .status("CREATED")
                .ownerId(UUID.randomUUID())
                .build();

        Mockito.when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDoc));
        Mockito.when(pipelineService.processDocument(documentId)).thenReturn(mockDoc);
    }

    @Test
    void testProcessResume_CacheMiss_CallsGatewayAndPersistsCache() {
        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.empty());

        AiTaskResponseDto mockParseResponse = AiTaskResponseDto.builder()
                .taskId(UUID.randomUUID())
                .status("COMPLETED")
                .provider("mock")
                .result(new HashMap<>())
                .metadata(new HashMap<>())
                .build();

        Mockito.when(gatewayClient.executeTask(any())).thenReturn(mockParseResponse);

        AiDocument result = resumeIntelligenceService.processResume(documentId);

        assertEquals("My CV", result.getTitle());
        // Verify parsing results cached
        verify(cacheRepository, times(1)).save(any(ResumeIntelligenceCache.class));
        // Verify validation report persisted
        verify(validationReportRepository, times(1)).save(any(ResumeValidationReport.class));
        // Verify event pipeline publisher triggered (7 events published)
        verify(eventPublisher, times(7)).publishEvent(any(Object.class));
    }

    @Test
    void testProcessResume_CacheHit_BypassesGatewayCalls() {
        ResumeIntelligenceCache cache = ResumeIntelligenceCache.builder()
                .checksumSha256("abc")
                .parsedText("Parsed")
                .structuredKnowledge(new HashMap<>())
                .qualityMetrics(new HashMap<>())
                .build();

        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.of(cache));

        AiDocument result = resumeIntelligenceService.processResume(documentId);

        assertEquals("My CV", result.getTitle());
        // Verify gateway calls bypassed
        verify(gatewayClient, times(0)).executeTask(any());
        // Verify event pipeline publisher triggered
        verify(eventPublisher, times(7)).publishEvent(any(Object.class));
    }
}
