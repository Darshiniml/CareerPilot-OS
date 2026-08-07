package com.careerpilot.backend.modules.ai.company.services;

import com.careerpilot.backend.modules.ai.company.domain.CompanyIntelligenceCache;
import com.careerpilot.backend.modules.ai.company.repositories.CompanyIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.careerpilot.shared.events.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class CompanyIntelligenceService {

    private final AiDocumentRepository documentRepository;
    private final CompanyIntelligenceCacheRepository cacheRepository;
    private final KnowledgePipelineService pipelineService;
    private final AiGatewayClient gatewayClient;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyIntelligenceService(
            AiDocumentRepository documentRepository,
            CompanyIntelligenceCacheRepository cacheRepository,
            KnowledgePipelineService pipelineService,
            AiGatewayClient gatewayClient,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.cacheRepository = cacheRepository;
        this.pipelineService = pipelineService;
        this.gatewayClient = gatewayClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AiDocument processCompany(UUID documentId, String content, String url) {
        AiDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Company document not found"));

        String textContent = content != null ? content : (document.getContent() != null ? document.getContent() : "Sample company data");
        String checksum = calculateChecksum(textContent.getBytes(StandardCharsets.UTF_8));
        document.setChecksum(checksum);
        document.setSource(url);

        Optional<CompanyIntelligenceCache> cacheOpt = cacheRepository.findById(checksum);
        Map<String, Object> knowledge;
        Map<String, Object> metadata;
        Map<String, Object> insights;

        UUID userId = document.getOwnerId();

        if (cacheOpt.isPresent()) {
            log.info("Company intelligence cache HIT for checksum: {}", checksum);
            CompanyIntelligenceCache cache = cacheOpt.get();
            knowledge = cache.getStructuredKnowledge();
            metadata = cache.getMetadata();
            insights = cache.getInsights();

            // Update cache change detection properties
            cache.setLastIndexedAt(Instant.now());
            cache.setCrawlTimestamp(Instant.now());
            cacheRepository.save(cache);

            eventPublisher.publishEvent(CompanyCacheUpdatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .companyId(documentId)
                    .userId(userId)
                    .checksum(checksum)
                    .build());
        } else {
            log.info("Company intelligence cache MISS for checksum: {}", checksum);

            // 1. COMPANY_PARSE to extract Canonical structure
            Map<String, Object> parsePayload = new HashMap<>();
            parsePayload.put("content", textContent);
            parsePayload.put("url", url);

            AiTaskRequestDto parseRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("COMPANY_PARSE")
                    .payload(parsePayload)
                    .build();

            AiTaskResponseDto parseResponse = gatewayClient.executeTask(parseRequest);
            knowledge = parseResponse.getResult();

            // 2. COMPANY_METADATA to compute metrics & categories
            Map<String, Object> metaPayload = new HashMap<>();
            metaPayload.put("knowledge", knowledge);

            AiTaskRequestDto metaRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("COMPANY_METADATA")
                    .payload(metaPayload)
                    .build();

            AiTaskResponseDto metaResponse = gatewayClient.executeTask(metaRequest);
            metadata = metaResponse.getResult();

            // 3. COMPANY_INSIGHTS to categorize
            Map<String, Object> insightsPayload = new HashMap<>();
            insightsPayload.put("knowledge", knowledge);
            insightsPayload.put("metadata", metadata);

            AiTaskRequestDto insightsRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("COMPANY_INSIGHTS")
                    .payload(insightsPayload)
                    .build();

            AiTaskResponseDto insightsResponse = gatewayClient.executeTask(insightsRequest);
            insights = insightsResponse.getResult();

            // Save to Cache Table
            CompanyIntelligenceCache cache = CompanyIntelligenceCache.builder()
                    .checksumSha256(checksum)
                    .acquiredContent(textContent)
                    .structuredKnowledge(knowledge)
                    .metadata(metadata)
                    .insights(insights)
                    .lastIndexedAt(Instant.now())
                    .sourceVersion("v1")
                    .crawlTimestamp(Instant.now())
                    .build();
            cacheRepository.save(cache);
        }

        // Store structured mapping in document metadata fields
        document.setStructuredMetadata(knowledge);
        Map<String, Object> flexibleCombined = new HashMap<>();
        flexibleCombined.put("metadata", metadata);
        flexibleCombined.put("insights", insights);
        document.setFlexibleMetadata(flexibleCombined);
        document.setContent(textContent);
        documentRepository.save(document);

        // Delegate to generic platform pipeline for chunking and embedding indexing
        AiDocument processed = pipelineService.processDocument(documentId);

        // Publish extended events chain
        publishEventChain(processed);

        return processed;
    }

    @Transactional
    public AiDocument processCompanyUrl(UUID ownerId, String url) {
        // Simulate source content acquisition
        String simulatedContent = acquireSimulatedContent(url);
        String title = extractTitleFromUrl(url);

        AiDocument document = AiDocument.builder()
                .id(UUID.randomUUID())
                .documentType("COMPANY")
                .status("CREATED")
                .ownerId(ownerId)
                .title(title)
                .source(url)
                .content(simulatedContent)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        documentRepository.save(document);

        eventPublisher.publishEvent(CompanyDiscoveredEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(document.getId())
                .userId(ownerId)
                .url(url)
                .build());

        return processCompany(document.getId(), simulatedContent, url);
    }

    private void publishEventChain(AiDocument doc) {
        UUID userId = doc.getOwnerId();
        UUID companyId = doc.getId();

        eventPublisher.publishEvent(CompanyParsedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(CompanyKnowledgeGeneratedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(CompanyMetadataGeneratedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(CompanyEmbeddedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(CompanyIndexedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(CompanyRetrievalReadyEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(CompanyAnalysisCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .companyId(companyId)
                .userId(userId)
                .build());
    }

    private String calculateChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String acquireSimulatedContent(String url) {
        String cleanUrl = url.toLowerCase();
        if (cleanUrl.contains("google")) {
            return "About Google: Search engine and cloud enterprise. We use Java, Go, and Python. We specialize in AI/ML.";
        } else if (cleanUrl.contains("netflix")) {
            return "About Netflix: Streaming service. Headquarters in Los Gatos, California. Tech stack consists of Java, React, and AWS.";
        } else {
            return "Company Website: Modern tech company focusing on backend software. Tech stack includes AWS, Docker, Java, and Spring Boot.";
        }
    }

    private String extractTitleFromUrl(String url) {
        try {
            String domain = url.replaceFirst("^(https?://)?(www\\.)?", "");
            int slashIndex = domain.indexOf('/');
            if (slashIndex > 0) {
                domain = domain.substring(0, slashIndex);
            }
            return domain.substring(0, 1).toUpperCase() + domain.substring(1);
        } catch (Exception e) {
            return "Generic Tech Company";
        }
    }
}
