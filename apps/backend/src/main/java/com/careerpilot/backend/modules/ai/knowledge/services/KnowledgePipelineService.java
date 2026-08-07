package com.careerpilot.backend.modules.ai.knowledge.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.*;
import com.careerpilot.backend.modules.ai.knowledge.repositories.*;
import com.careerpilot.shared.dto.ai.*;
import com.careerpilot.shared.events.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class KnowledgePipelineService {

    private final AiDocumentRepository documentRepository;
    private final AiChunkRepository chunkRepository;
    private final EmbeddingReferenceRepository embeddingReferenceRepository;
    private final AiGatewayClient gatewayClient;
    private final ApplicationEventPublisher eventPublisher;

    public KnowledgePipelineService(
            AiDocumentRepository documentRepository,
            AiChunkRepository chunkRepository,
            EmbeddingReferenceRepository embeddingReferenceRepository,
            AiGatewayClient gatewayClient,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingReferenceRepository = embeddingReferenceRepository;
        this.gatewayClient = gatewayClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AiDocument processDocument(UUID documentId) {
        AiDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        try {
            validate(document);
            parse(document);
            extractMetadata(document);
            chunk(document);
            embed(document);
            index(document);

            document.setStatus("READY");
            document.setUpdatedAt(Instant.now());
            AiDocument saved = documentRepository.save(document);

            eventPublisher.publishEvent(DocumentReadyEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .documentId(saved.getId())
                    .userId(saved.getOwnerId())
                    .build());

            log.info("AI Knowledge pipeline completed successfully for document ID: {}", documentId);
            return saved;

        } catch (Exception e) {
            log.error("AI Knowledge pipeline failed for document ID: {}", documentId, e);
            document.setStatus("FAILED");
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            throw new RuntimeException("Knowledge pipeline execution failed", e);
        }
    }

    private void validate(AiDocument doc) {
        if (doc.getTitle() == null || doc.getTitle().isBlank()) {
            throw new IllegalArgumentException("Document title is empty");
        }
        doc.setStatus("VALIDATED");
        documentRepository.save(doc);
        eventPublisher.publishEvent(DocumentValidatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .documentId(doc.getId())
                .userId(doc.getOwnerId())
                .build());
    }

    private void parse(AiDocument doc) {
        if (doc.getContent() == null) {
            doc.setContent("Mocked parsed text content for document: " + doc.getTitle());
        }
        doc.setStatus("PARSED");
        documentRepository.save(doc);
    }

    private void extractMetadata(AiDocument doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", doc.getOwnerId() != null ? doc.getOwnerId().toString() : "");
        payload.put("documentType", doc.getDocumentType());

        AiTaskRequestDto requestDto = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType("RESUME_PARSE")
                .payload(payload)
                .build();

        AiTaskResponseDto response = gatewayClient.executeTask(requestDto);
        doc.setStructuredMetadata(response.getResult());
        doc.setFlexibleMetadata(response.getMetadata());
        
        doc.setStatus("METADATA_EXTRACTED");
        documentRepository.save(doc);

        eventPublisher.publishEvent(MetadataExtractedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .documentId(doc.getId())
                .userId(doc.getOwnerId())
                .build());
    }

    private void chunk(AiDocument doc) {
        String text = doc.getContent();
        List<String> textChunks = new ArrayList<>();
        int length = text.length();
        int size = 500;
        int overlap = 100;
        int start = 0;
        
        while (start < length) {
            int end = Math.min(start + size, length);
            textChunks.add(text.substring(start, end));
            start += (size - overlap);
        }

        List<AiChunk> chunks = new ArrayList<>();
        for (int i = 0; i < textChunks.size(); i++) {
            AiChunk chunk = AiChunk.builder()
                    .id(UUID.randomUUID())
                    .document(doc)
                    .chunkNumber(i + 1)
                    .text(textChunks.get(i))
                    .tokenCount(textChunks.get(i).split("\\s+").length)
                    .sourceDocumentVersion(doc.getVersion())
                    .metadata(new HashMap<>())
                    .build();
            chunks.add(chunk);
        }
        chunkRepository.saveAll(chunks);
        doc.getChunks().addAll(chunks);

        doc.setStatus("CHUNKED");
        documentRepository.save(doc);

        eventPublisher.publishEvent(DocumentChunkedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .documentId(doc.getId())
                .userId(doc.getOwnerId())
                .chunkCount(chunks.size())
                .build());
    }

    private void embed(AiDocument doc) {
        for (AiChunk chunk : doc.getChunks()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chunkText", chunk.getText());

            AiTaskRequestDto requestDto = AiTaskRequestDto.builder()
                    .taskId(UUID.randomUUID())
                    .taskType("GENERATE_EMBEDDINGS")
                    .payload(payload)
                    .build();

            AiTaskResponseDto response = gatewayClient.executeTask(requestDto);

            EmbeddingReference ref = EmbeddingReference.builder()
                    .id(UUID.randomUUID())
                    .document(doc)
                    .chunk(chunk)
                    .provider(response.getProvider())
                    .collection(doc.getDocumentType().toLowerCase() + "_vectors")
                    .vectorId(UUID.randomUUID().toString())
                    .embeddingModel("all-MiniLM-L6-v2")
                    .embeddingVersion("v1")
                    .vectorDimension(384)
                    .build();
            embeddingReferenceRepository.save(ref);
        }

        doc.setStatus("EMBEDDED");
        documentRepository.save(doc);

        eventPublisher.publishEvent(EmbeddingsGeneratedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .documentId(doc.getId())
                .userId(doc.getOwnerId())
                .build());
    }

    private void index(AiDocument doc) {
        doc.setStatus("INDEXED");
        documentRepository.save(doc);

        eventPublisher.publishEvent(DocumentIndexedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .documentId(doc.getId())
                .userId(doc.getOwnerId())
                .build());
    }
}
