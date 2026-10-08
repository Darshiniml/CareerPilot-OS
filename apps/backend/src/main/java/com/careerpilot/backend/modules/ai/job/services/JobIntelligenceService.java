package com.careerpilot.backend.modules.ai.job.services;

import com.careerpilot.backend.modules.ai.web.SafeWebPageFetcher;
import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class JobIntelligenceService {

    private final AiDocumentRepository documentRepository;
    private final JobIntelligenceCacheRepository cacheRepository;
    private final KnowledgePipelineService pipelineService;
    private final AiGatewayClient gatewayClient;
    private final ApplicationEventPublisher eventPublisher;
    private final SafeWebPageFetcher webPageFetcher;

    public JobIntelligenceService(
            AiDocumentRepository documentRepository,
            JobIntelligenceCacheRepository cacheRepository,
            KnowledgePipelineService pipelineService,
            AiGatewayClient gatewayClient,
            ApplicationEventPublisher eventPublisher,
            SafeWebPageFetcher webPageFetcher) {
        this.webPageFetcher = webPageFetcher;
        this.documentRepository = documentRepository;
        this.cacheRepository = cacheRepository;
        this.pipelineService = pipelineService;
        this.gatewayClient = gatewayClient;
        this.eventPublisher = eventPublisher;
    }

    // Not transactional: the model calls below can take minutes on a local CPU model, and a
    // transaction would pin a pooled DB connection for that whole time. Each save commits on its own.
    public AiDocument processJob(UUID documentId, String content, String url) {
        AiDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Job document not found"));

        String textContent = content != null ? content : document.getContent();
        if (textContent == null || textContent.isBlank()) {
            throw new IllegalArgumentException("Job description text is required");
        }
        String checksum = calculateChecksum(textContent.getBytes(StandardCharsets.UTF_8));
        document.setChecksum(checksum);
        document.setSource(url);

        UUID userId = document.getOwnerId();

        // 1. Duplicate Job Detection Check
        Optional<AiDocument> duplicateOpt = documentRepository
                .findFirstByDocumentTypeAndChecksumAndIdNot("JOB", checksum, documentId)
                .filter(d -> "READY".equals(d.getStatus()));

        if (duplicateOpt.isPresent()) {
            log.info("Duplicate job detected. Staging bypassed for document: {}", documentId);
            return duplicateOpt.get();
        }

        Optional<JobIntelligenceCache> cacheOpt = cacheRepository.findById(checksum);
        Map<String, Object> knowledge;
        Map<String, Object> metadata;
        Map<String, Object> qualityMetrics;
        Map<String, Object> insights;

        if (cacheOpt.isPresent()) {
            log.info("Job intelligence cache HIT for checksum: {}", checksum);
            JobIntelligenceCache cache = cacheOpt.get();
            knowledge = cache.getStructuredKnowledge();
            metadata = cache.getMetadata();
            qualityMetrics = cache.getQualityMetrics();
            insights = cache.getInsights();

            // Update cache change detection timestamps
            cache.setLastIndexedAt(Instant.now());
            cache.setCrawlTimestamp(Instant.now());
            cacheRepository.save(cache);

            eventPublisher.publishEvent(JobCacheUpdatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .jobId(documentId)
                    .userId(userId)
                    .checksum(checksum)
                    .build());
        } else {
            log.info("Job intelligence cache MISS for checksum: {}", checksum);

            // A. Execute JOB_PARSE to extract Canonical properties
            Map<String, Object> parsePayload = new HashMap<>();
            parsePayload.put("content", textContent);
            if (url != null) {
                parsePayload.put("sourceUrl", url);
            }

            AiTaskRequestDto parseRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("JOB_PARSE")
                    .payload(parsePayload)
                    .build();

            AiTaskResponseDto parseResponse = gatewayClient.executeTask(parseRequest);
            knowledge = parseResponse.getResult();

            // B. Execute JOB_METADATA to compute categorizations and metrics
            Map<String, Object> metaPayload = new HashMap<>();
            metaPayload.put("knowledge", knowledge);

            AiTaskRequestDto metaRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("JOB_METADATA")
                    .payload(metaPayload)
                    .build();

            AiTaskResponseDto metaResponse = gatewayClient.executeTask(metaRequest);
            Map<String, Object> combinedMetaResult = metaResponse.getResult();
            
            metadata = (Map<String, Object>) combinedMetaResult.getOrDefault("metadata", new HashMap<>());
            qualityMetrics = (Map<String, Object>) combinedMetaResult.getOrDefault("qualityMetrics", new HashMap<>());

            // C. Execute JOB_INSIGHTS to determine insights
            Map<String, Object> insightsPayload = new HashMap<>();
            insightsPayload.put("knowledge", knowledge);
            insightsPayload.put("metadata", metadata);

            AiTaskRequestDto insightsRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("JOB_INSIGHTS")
                    .payload(insightsPayload)
                    .build();

            AiTaskResponseDto insightsResponse = gatewayClient.executeTask(insightsRequest);
            insights = insightsResponse.getResult();

            // Save to Cache Table
            JobIntelligenceCache cache = JobIntelligenceCache.builder()
                    .checksumSha256(checksum)
                    .parsedText(textContent)
                    .structuredKnowledge(knowledge)
                    .metadata(metadata)
                    .qualityMetrics(qualityMetrics)
                    .insights(insights)
                    .lastIndexedAt(Instant.now())
                    .sourceVersion("v1")
                    .crawlTimestamp(Instant.now())
                    .build();
            cacheRepository.save(cache);
        }

        // Save structured mappings to document fields
        document.setStructuredMetadata(knowledge);
        Map<String, Object> flexibleCombined = new HashMap<>();
        flexibleCombined.put("metadata", metadata);
        flexibleCombined.put("qualityMetrics", qualityMetrics);
        flexibleCombined.put("insights", insights);
        document.setFlexibleMetadata(flexibleCombined);
        document.setContent(textContent);
        documentRepository.save(document);

        // Run the generic processing pipeline (chunking + embedding indexing)
        AiDocument processed = pipelineService.processDocument(documentId);

        // Publish events chain
        publishEventChain(processed);

        return processed;
    }

    /** Fetch a real job posting page (SSRF-protected) and analyse its text. */
    public AiDocument processJobUrl(UUID ownerId, String url) {
        SafeWebPageFetcher.FetchedPage page = webPageFetcher.fetch(url);
        if (page.text() == null || page.text().split("\\s+").length < 30) {
            throw new IllegalArgumentException("The page does not contain enough readable text to analyse "
                    + "(it may require JavaScript or a login). Paste the job description instead.");
        }
        String pageContent = page.text().length() > 24000 ? page.text().substring(0, 24000) : page.text();
        String title = page.title() != null && !page.title().isBlank() ? page.title() : page.finalUrl();

        AiDocument document = AiDocument.builder()
                .id(UUID.randomUUID())
                .documentType("JOB")
                .status("CREATED")
                .ownerId(ownerId)
                .title(title)
                .source(url)
                .content(pageContent)
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        documentRepository.save(document);

        eventPublisher.publishEvent(JobDiscoveredEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(document.getId())
                .userId(ownerId)
                .url(url)
                .build());

        return processJob(document.getId(), pageContent, url);
    }

    private void publishEventChain(AiDocument doc) {
        UUID userId = doc.getOwnerId();
        UUID jobId = doc.getId();

        eventPublisher.publishEvent(JobParsedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobRequirementsExtractedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobSkillsNormalizedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobKnowledgeGeneratedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobMetadataGeneratedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobMetricsGeneratedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobEmbeddedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobIndexedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobRetrievalReadyEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(JobAnalysisCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .jobId(jobId)
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

}
