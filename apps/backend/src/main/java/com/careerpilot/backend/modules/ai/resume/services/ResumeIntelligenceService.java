package com.careerpilot.backend.modules.ai.resume.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeValidationReport;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeValidationReportRepository;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.careerpilot.shared.events.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class ResumeIntelligenceService {

    private final AiDocumentRepository documentRepository;
    private final ResumeIntelligenceCacheRepository cacheRepository;
    private final ResumeValidationReportRepository validationReportRepository;
    private final KnowledgePipelineService pipelineService;
    private final AiGatewayClient gatewayClient;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeIntelligenceService(
            AiDocumentRepository documentRepository,
            ResumeIntelligenceCacheRepository cacheRepository,
            ResumeValidationReportRepository validationReportRepository,
            KnowledgePipelineService pipelineService,
            AiGatewayClient gatewayClient,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.cacheRepository = cacheRepository;
        this.validationReportRepository = validationReportRepository;
        this.pipelineService = pipelineService;
        this.gatewayClient = gatewayClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AiDocument processResume(UUID documentId) {
        AiDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Resume document not found"));

        String textContent = document.getContent() != null ? document.getContent() : "Sample resume details";
        String checksum = calculateChecksum(textContent.getBytes());
        document.setChecksum(checksum);

        Optional<ResumeIntelligenceCache> cacheOpt = cacheRepository.findById(checksum);
        Map<String, Object> knowledge;
        Map<String, Object> qualityMetrics;

        if (cacheOpt.isPresent()) {
            log.info("Resume intelligence parsing cache HIT for checksum: {}", checksum);
            ResumeIntelligenceCache cache = cacheOpt.get();
            knowledge = cache.getStructuredKnowledge();
            qualityMetrics = cache.getQualityMetrics();
        } else {
            log.info("Resume intelligence parsing cache MISS for checksum: {}", checksum);
            
            Map<String, Object> parsePayload = new HashMap<>();
            parsePayload.put("title", document.getTitle());
            parsePayload.put("documentType", "RESUME");
            parsePayload.put("content", textContent);

            AiTaskRequestDto parseRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("RESUME_PARSE")
                    .payload(parsePayload)
                    .build();

            AiTaskResponseDto parseResponse = gatewayClient.executeTask(parseRequest);
            knowledge = parseResponse.getResult();

            Map<String, Object> atsPayload = new HashMap<>();
            atsPayload.put("knowledge", knowledge);

            AiTaskRequestDto atsRequest = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("JOB_MATCH")
                    .payload(atsPayload)
                    .build();

            AiTaskResponseDto atsResponse = gatewayClient.executeTask(atsRequest);
            qualityMetrics = atsResponse.getResult();

            ResumeIntelligenceCache cache = ResumeIntelligenceCache.builder()
                    .checksumSha256(checksum)
                    .parsedText(textContent)
                    .structuredKnowledge(knowledge)
                    .qualityMetrics(qualityMetrics)
                    .build();
            cacheRepository.save(cache);
        }

        document.setStructuredMetadata(knowledge);
        document.setFlexibleMetadata(qualityMetrics);
        
        runValidation(document, textContent);

        AiDocument processed = pipelineService.processDocument(documentId);

        publishEventChain(processed);

        return processed;
    }

    private void runValidation(AiDocument doc, String content) {
        boolean hasEmail = content.contains("@");
        boolean hasPhone = content.matches(".*\\d{10}.*") || content.matches(".*\\d{3}-\\d{3}-\\d{4}.*");
        boolean hasLinkedin = content.toLowerCase().contains("linkedin.com");

        List<String> warnings = new ArrayList<>();
        if (!hasEmail) warnings.add("Missing email address");
        if (!hasPhone) warnings.add("Missing phone number");
        if (!hasLinkedin) warnings.add("Missing LinkedIn profile URL");

        Map<String, Object> warningsMap = new HashMap<>();
        warningsMap.put("warnings", warnings);

        ResumeValidationReport report = ResumeValidationReport.builder()
                .id(UUID.randomUUID())
                .documentId(doc.getId())
                .hasEmail(hasEmail)
                .hasPhone(hasPhone)
                .hasLinkedin(hasLinkedin)
                .validationWarnings(warningsMap)
                .build();
        validationReportRepository.save(report);

        eventPublisher.publishEvent(ResumeValidatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(doc.getId())
                .userId(doc.getOwnerId())
                .build());
    }

    private void publishEventChain(AiDocument doc) {
        UUID userId = doc.getOwnerId();
        UUID resumeId = doc.getId();

        eventPublisher.publishEvent(ResumeParsedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(resumeId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(SkillsExtractedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(resumeId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(ResumeEmbeddedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(resumeId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(ResumeSearchIndexedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(resumeId)
                .userId(userId)
                .build());

        eventPublisher.publishEvent(ResumeAnalysisCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(resumeId)
                .userId(userId)
                .build());
        
        eventPublisher.publishEvent(ResumeIndexedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .resumeId(resumeId)
                .userId(userId)
                .build());
    }

    private String calculateChecksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }
}
