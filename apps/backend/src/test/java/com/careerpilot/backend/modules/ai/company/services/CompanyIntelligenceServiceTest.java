package com.careerpilot.backend.modules.ai.company.services;

import com.careerpilot.backend.modules.ai.web.SafeWebPageFetcher;

import com.careerpilot.backend.modules.ai.company.domain.CompanyIntelligenceCache;
import com.careerpilot.backend.modules.ai.company.repositories.CompanyIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
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

class CompanyIntelligenceServiceTest {

    private final SafeWebPageFetcher webPageFetcher = Mockito.mock(SafeWebPageFetcher.class);

    private AiDocumentRepository documentRepository;
    private CompanyIntelligenceCacheRepository cacheRepository;
    private KnowledgePipelineService pipelineService;
    private AiGatewayClient gatewayClient;
    private ApplicationEventPublisher eventPublisher;

    private CompanyIntelligenceService companyIntelligenceService;
    private UUID documentId;
    private AiDocument mockDoc;

    @BeforeEach
    void setUp() {
        documentRepository = Mockito.mock(AiDocumentRepository.class);
        cacheRepository = Mockito.mock(CompanyIntelligenceCacheRepository.class);
        pipelineService = Mockito.mock(KnowledgePipelineService.class);
        gatewayClient = Mockito.mock(AiGatewayClient.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        companyIntelligenceService = new CompanyIntelligenceService(
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
                .documentType("COMPANY")
                .title("Google Website")
                .content("Welcome to Google. We use Java and Go.")
                .status("CREATED")
                .ownerId(UUID.randomUUID())
                .build();

        Mockito.when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockDoc));
        Mockito.when(pipelineService.processDocument(any(UUID.class))).thenReturn(mockDoc);
    }

    @Test
    void testProcessCompany_CacheMiss_ExecutesTasksAndSavesCache() {
        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.empty());

        AiTaskResponseDto mockResponse = AiTaskResponseDto.builder()
                .taskId(UUID.randomUUID())
                .status("COMPLETED")
                .provider("mock")
                .result(new HashMap<>())
                .metadata(new HashMap<>())
                .build();

        Mockito.when(gatewayClient.executeTask(any())).thenReturn(mockResponse);

        AiDocument result = companyIntelligenceService.processCompany(documentId, "Welcome to Google. We use Java and Go.", "https://google.com");

        assertEquals("Google Website", result.getTitle());
        // Verify parse, metadata, and insights tasks executed
        verify(gatewayClient, times(3)).executeTask(any());
        // Verify cache entry saved
        verify(cacheRepository, times(1)).save(any(CompanyIntelligenceCache.class));
        // Verify pipeline called
        verify(pipelineService, times(1)).processDocument(documentId);
        // Verify events published (7 events in pipeline chain)
        verify(eventPublisher, times(7)).publishEvent(any(Object.class));
    }

    @Test
    void testProcessCompany_CacheHit_BypassesTaskExecution() {
        CompanyIntelligenceCache cache = CompanyIntelligenceCache.builder()
                .checksumSha256("abc")
                .acquiredContent("content")
                .structuredKnowledge(new HashMap<>())
                .metadata(new HashMap<>())
                .insights(new HashMap<>())
                .build();

        Mockito.when(cacheRepository.findById(any(String.class))).thenReturn(Optional.of(cache));

        AiDocument result = companyIntelligenceService.processCompany(documentId, "Welcome to Google. We use Java and Go.", "https://google.com");

        assertEquals("Google Website", result.getTitle());
        // Verify gateway calls bypassed
        verify(gatewayClient, times(0)).executeTask(any());
        // Verify cache updated (timestamp)
        verify(cacheRepository, times(1)).save(any(CompanyIntelligenceCache.class));
        // Verify events published (1 cache update + 7 pipeline chain = 8 events)
        verify(eventPublisher, times(8)).publishEvent(any(Object.class));
    }

    @Test
    void testProcessCompanyUrl_CreatesDocumentAndStartsPipeline() {
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
        // Setup repository mock mapping for final pipeline document find
        Mockito.when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockDoc));

        Mockito.when(webPageFetcher.fetch("https://acme.example/about")).thenReturn(
                new SafeWebPageFetcher.FetchedPage("https://acme.example/about", "About Acme", "Acme Payments builds payment infrastructure for online merchants across India. Our engineering team uses Java, Kotlin, PostgreSQL and Kubernetes on AWS. We value ownership, code review and continuous delivery, and we hire engineers who enjoy working closely with product teams."));

        AiDocument result = companyIntelligenceService.processCompanyUrl(ownerId, "https://acme.example/about");

        assertEquals("Google Website", result.getTitle());
        // The fetched page text (not canned company text) is what gets analysed.
        org.mockito.Mockito.verify(gatewayClient, org.mockito.Mockito.atLeastOnce()).executeTask(
                org.mockito.ArgumentMatchers.argThat(req -> "COMPANY_PARSE".equals(req.getTaskType())
                        && String.valueOf(req.getPayload().get("content")).startsWith("Acme Payments builds")));
        // Verify 1 discovered event + 7 pipeline chain = 8 events
        verify(eventPublisher, times(8)).publishEvent(any(Object.class));
    }
}
