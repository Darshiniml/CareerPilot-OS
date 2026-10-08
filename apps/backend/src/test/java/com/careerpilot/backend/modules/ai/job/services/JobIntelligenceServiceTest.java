package com.careerpilot.backend.modules.ai.job.services;

import com.careerpilot.backend.modules.ai.web.SafeWebPageFetcher;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JobIntelligenceServiceTest {

    private static final String PAGE_TEXT = "Senior Backend Engineer at Acme Payments. We are hiring an engineer to build payment APIs in Java and Spring Boot on PostgreSQL. Requirements: five years of backend experience, strong testing habits, experience operating services in production, and clear written communication with the team.";

    private AiDocumentRepository documentRepository;
    private JobIntelligenceCacheRepository cacheRepository;
    private KnowledgePipelineService pipelineService;
    private AiGatewayClient gatewayClient;
    private ApplicationEventPublisher eventPublisher;
    private SafeWebPageFetcher webPageFetcher;

    private JobIntelligenceService jobIntelligenceService;
    private UUID documentId;
    private AiDocument mockDoc;

    @BeforeEach
    void setUp() {
        documentRepository = Mockito.mock(AiDocumentRepository.class);
        cacheRepository = Mockito.mock(JobIntelligenceCacheRepository.class);
        pipelineService = Mockito.mock(KnowledgePipelineService.class);
        gatewayClient = Mockito.mock(AiGatewayClient.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        webPageFetcher = Mockito.mock(SafeWebPageFetcher.class);

        jobIntelligenceService = new JobIntelligenceService(
                documentRepository,
                cacheRepository,
                pipelineService,
                gatewayClient,
                eventPublisher,
                webPageFetcher
        );

        documentId = UUID.randomUUID();
        mockDoc = AiDocument.builder()
                .id(documentId)
                .documentType("JOB")
                .title("Software Engineer")
                .content("Requirements: Java skills.")
                .status("CREATED")
                .ownerId(UUID.randomUUID())
                .build();

        Mockito.when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockDoc));
        Mockito.when(pipelineService.processDocument(any(UUID.class))).thenReturn(mockDoc);
    }

    @Test
    void testProcessJob_CacheMiss_ExecutesTasksAndSavesCache() {
        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.empty());

        AiTaskResponseDto mockResponse = AiTaskResponseDto.builder()
                .taskId(UUID.randomUUID())
                .status("COMPLETED")
                .provider("mock")
                .result(new HashMap<>())
                .metadata(new HashMap<>())
                .build();

        Mockito.when(gatewayClient.executeTask(any())).thenReturn(mockResponse);

        AiDocument result = jobIntelligenceService.processJob(documentId, "Requirements: Java skills.", "https://google.com");

        assertNotNull(result);
        assertEquals("Software Engineer", result.getTitle());
        // Verify parse, metadata, and insights tasks executed
        verify(gatewayClient, times(3)).executeTask(any());
        // Verify cache entry saved
        verify(cacheRepository, times(1)).save(any(JobIntelligenceCache.class));
        // Verify pipeline called
        verify(pipelineService, times(1)).processDocument(any(UUID.class));
        // Verify events published (10 events in pipeline chain)
        verify(eventPublisher, times(10)).publishEvent(any(Object.class));
    }

    @Test
    void testProcessJob_CacheHit_BypassesTaskExecution() {
        JobIntelligenceCache cache = JobIntelligenceCache.builder()
                .checksumSha256("abc")
                .parsedText("content")
                .structuredKnowledge(new HashMap<>())
                .metadata(new HashMap<>())
                .qualityMetrics(new HashMap<>())
                .insights(new HashMap<>())
                .build();

        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.of(cache));

        AiDocument result = jobIntelligenceService.processJob(documentId, "Requirements: Java skills.", "https://google.com");

        assertNotNull(result);
        // Verify gateway calls bypassed
        verify(gatewayClient, times(0)).executeTask(any());
        // Verify cache updated (timestamp)
        verify(cacheRepository, times(1)).save(any(JobIntelligenceCache.class));
        // Verify events published (1 cache update + 10 pipeline chain = 11 events)
        verify(eventPublisher, times(11)).publishEvent(any(Object.class));
    }

    @Test
    void testProcessJob_DuplicateDetected_BypassesIngestion() {
        // Mock a duplicate document in repo
        AiDocument duplicateDoc = AiDocument.builder()
                .id(UUID.randomUUID())
                .documentType("JOB")
                .title("Duplicate Software Engineer")
                .checksum("eee99828fe48e6f4586cc8c70fe4f4c70f75e0ead4e32f1706f72ecc7e6b4716")
                .status("READY")
                .build();

        Mockito.when(documentRepository.findFirstByDocumentTypeAndChecksumAndIdNot(eq("JOB"), any(), eq(documentId)))
                .thenReturn(Optional.of(duplicateDoc));
        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.empty());

        AiDocument result = jobIntelligenceService.processJob(documentId, "Requirements: Java skills.", "https://google.com");

        // Verify duplicate returned directly
        assertEquals(duplicateDoc.getId(), result.getId());
        verify(pipelineService, times(0)).processDocument(any());
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    void processJobRequiresRealContent() {
        mockDoc.setContent(null);
        assertThrows(IllegalArgumentException.class, () -> jobIntelligenceService.processJob(documentId, null, null));
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void processJobUrlRejectsPagesWithoutReadableText() {
        Mockito.when(webPageFetcher.fetch(any())).thenReturn(
                new SafeWebPageFetcher.FetchedPage("https://spa.example", "App", "Loading..."));
        assertThrows(IllegalArgumentException.class,
                () -> jobIntelligenceService.processJobUrl(UUID.randomUUID(), "https://spa.example"));
        verifyNoInteractions(gatewayClient);
    }

    @Test
    void testProcessJobUrl_CreatesDocumentAndStartsPipeline() {
        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.empty());
        Mockito.when(documentRepository.save(any(AiDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiTaskResponseDto mockResponse = AiTaskResponseDto.builder()
                .taskId(UUID.randomUUID())
                .status("COMPLETED")
                .provider("mock")
                .result(new HashMap<>())
                .metadata(new HashMap<>())
                .build();
        Mockito.when(gatewayClient.executeTask(any())).thenReturn(mockResponse);

        UUID ownerId = UUID.randomUUID();
        Mockito.when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockDoc));
        Mockito.when(webPageFetcher.fetch("https://jobs.acme.example/123")).thenReturn(
                new SafeWebPageFetcher.FetchedPage("https://jobs.acme.example/123", "Senior Backend Engineer", PAGE_TEXT));

        AiDocument result = jobIntelligenceService.processJobUrl(ownerId, "https://jobs.acme.example/123");

        assertNotNull(result);
        // The real fetched page text is what gets parsed (never canned content).
        verify(gatewayClient, atLeastOnce()).executeTask(argThat(req ->
                "JOB_PARSE".equals(req.getTaskType()) && PAGE_TEXT.equals(req.getPayload().get("content"))));
        // Verify 1 discovered event + 10 pipeline chain = 11 events
        verify(eventPublisher, times(11)).publishEvent(any(Object.class));
    }
}
