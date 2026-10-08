package com.careerpilot.backend.modules.ai.knowledge.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiChunk;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.domain.EmbeddingReference;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiChunkRepository;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.repositories.EmbeddingReferenceRepository;
import com.careerpilot.shared.events.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Document knowledge pipeline: validate → (parse) → chunk + embed + index in Qdrant → READY.
 *
 * <p>Chunking, embedding and vector storage happen in the AI service ({@code DOCUMENT_INDEX}), which
 * returns the real vector ids and embedding model; this service persists matching chunk and
 * embedding-reference rows. Documents without real content fail: no placeholder text is ever
 * substituted. Candidate-owned document types must have an owner (vector isolation).</p>
 */
@Service
@Slf4j
public class KnowledgePipelineService {

    /** Must match PRIVATE_DOC_TYPES in the AI service's vector store. */
    static final Set<String> PRIVATE_DOC_TYPES = Set.of(
            "RESUME", "PROFILE", "COMMUNICATION", "INTERVIEW", "APPLICATION", "LEARNING", "CONVERSATION", "COVER_LETTER");

    private static final Map<String, String> PARSE_TASKS = Map.of(
            "RESUME", "RESUME_PARSE",
            "JOB", "JOB_PARSE",
            "COMPANY", "COMPANY_PARSE");

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
            extractMetadataIfMissing(document);
            indexAndRecord(document);

            document.setStatus("READY");
            document.setUpdatedAt(Instant.now());
            AiDocument saved = documentRepository.save(document);
            eventPublisher.publishEvent(DocumentReadyEvent.builder()
                    .eventId(UUID.randomUUID()).timestamp(Instant.now())
                    .documentId(saved.getId()).userId(saved.getOwnerId()).build());
            log.info("Knowledge pipeline completed for document {}", documentId);
            return saved;
        } catch (RuntimeException e) {
            log.error("Knowledge pipeline failed for document {}: {}", documentId, e.getMessage());
            document.setStatus("FAILED");
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            throw e;
        }
    }

    private void validate(AiDocument doc) {
        if (doc.getTitle() == null || doc.getTitle().isBlank()) {
            throw new IllegalArgumentException("Document title is empty");
        }
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            throw new IllegalArgumentException("Document has no extracted text content to process");
        }
        String type = doc.getDocumentType() == null ? "" : doc.getDocumentType().toUpperCase(Locale.ROOT);
        if (PRIVATE_DOC_TYPES.contains(type) && doc.getOwnerId() == null) {
            throw new IllegalArgumentException(type + " documents must have an owner");
        }
        doc.setStatus("VALIDATED");
        documentRepository.save(doc);
        eventPublisher.publishEvent(DocumentValidatedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now())
                .documentId(doc.getId()).userId(doc.getOwnerId()).build());
    }

    private void extractMetadataIfMissing(AiDocument doc) {
        if (doc.getStructuredMetadata() != null && !doc.getStructuredMetadata().isEmpty()) {
            return; // already parsed by the domain service (e.g. resume intelligence)
        }
        String task = PARSE_TASKS.get(doc.getDocumentType().toUpperCase(Locale.ROOT));
        if (task == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", doc.getContent());
        payload.put("documentType", doc.getDocumentType());
        if (doc.getSource() != null && doc.getSource().startsWith("http")) {
            payload.put("sourceUrl", doc.getSource());
        }
        doc.setStructuredMetadata(gatewayClient.run(task, payload));
        doc.setStatus("METADATA_EXTRACTED");
        documentRepository.save(doc);
        eventPublisher.publishEvent(MetadataExtractedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now())
                .documentId(doc.getId()).userId(doc.getOwnerId()).build());
    }

    @SuppressWarnings("unchecked")
    private void indexAndRecord(AiDocument doc) {
        String type = doc.getDocumentType().toUpperCase(Locale.ROOT);
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentType", type);
        payload.put("documentId", doc.getId().toString());
        payload.put("content", doc.getContent());
        if (doc.getOwnerId() != null) {
            payload.put("ownerId", doc.getOwnerId().toString());
        }
        payload.put("metadata", Map.of("title", doc.getTitle(), "version", doc.getVersion()));

        Map<String, Object> result = gatewayClient.run("DOCUMENT_INDEX", payload);
        List<String> texts = (List<String>) result.getOrDefault("chunkTexts", List.of());
        List<String> vectorIds = (List<String>) result.getOrDefault("vectorIds", List.of());
        if (texts.isEmpty() || texts.size() != vectorIds.size()) {
            throw new IllegalStateException("Vector index returned inconsistent chunks/vectors");
        }

        // Replace previous chunks/vectors records for this document (re-index is idempotent).
        embeddingReferenceRepository.deleteAll(embeddingReferenceRepository.findByDocumentId(doc.getId()));
        chunkRepository.deleteAll(chunkRepository.findByDocumentId(doc.getId()));
        doc.getChunks().clear();

        String provider = String.valueOf(result.get("embeddingProvider"));
        String model = String.valueOf(result.get("embeddingModel"));
        String collection = String.valueOf(result.get("collection"));
        int dimension = ((Number) result.getOrDefault("dimension", 0)).intValue();

        List<AiChunk> chunks = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            chunks.add(AiChunk.builder()
                    .id(UUID.randomUUID())
                    .document(doc)
                    .chunkNumber(i + 1)
                    .text(texts.get(i))
                    .tokenCount(texts.get(i).split("\\s+").length)
                    .sourceDocumentVersion(doc.getVersion())
                    .metadata(new HashMap<>())
                    .build());
        }
        chunkRepository.saveAll(chunks);
        doc.getChunks().addAll(chunks);
        eventPublisher.publishEvent(DocumentChunkedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now())
                .documentId(doc.getId()).userId(doc.getOwnerId()).chunkCount(chunks.size()).build());

        List<EmbeddingReference> refs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            refs.add(EmbeddingReference.builder()
                    .id(UUID.randomUUID())
                    .document(doc)
                    .chunk(chunks.get(i))
                    .provider(provider)
                    .collection(collection)
                    .vectorId(vectorIds.get(i))
                    .embeddingModel(model)
                    .embeddingVersion("v1")
                    .vectorDimension(dimension)
                    .build());
        }
        embeddingReferenceRepository.saveAll(refs);
        doc.setStatus("INDEXED");
        documentRepository.save(doc);
        eventPublisher.publishEvent(EmbeddingsGeneratedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now())
                .documentId(doc.getId()).userId(doc.getOwnerId()).build());
        eventPublisher.publishEvent(DocumentIndexedEvent.builder()
                .eventId(UUID.randomUUID()).timestamp(Instant.now())
                .documentId(doc.getId()).userId(doc.getOwnerId()).build());
    }

    /** Remove a document's vectors from the vector store (e.g. when a resume is deleted). */
    public void removeFromIndex(AiDocument doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentType", doc.getDocumentType().toUpperCase(Locale.ROOT));
        payload.put("documentId", doc.getId().toString());
        if (doc.getOwnerId() != null) {
            payload.put("ownerId", doc.getOwnerId().toString());
        }
        gatewayClient.run("DOCUMENT_DELETE", payload);
    }
}
