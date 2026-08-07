package com.careerpilot.backend.modules.ai.knowledge.adapters.in.web;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.knowledge.services.KnowledgePipelineService;
import com.careerpilot.shared.dto.ai.AiDocumentDto;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Knowledge Platform", description = "Endpoints for managing document lifecycles, chunking, and semantic retrievals")
@SecurityRequirement(name = "bearerAuth")
public class AiKnowledgeController {

    private final AiDocumentRepository documentRepository;
    private final KnowledgePipelineService pipelineService;
    private final AiGatewayClient gatewayClient;

    public AiKnowledgeController(
            AiDocumentRepository documentRepository,
            KnowledgePipelineService pipelineService,
            AiGatewayClient gatewayClient) {
        this.documentRepository = documentRepository;
        this.pipelineService = pipelineService;
        this.gatewayClient = gatewayClient;
    }

    @PostMapping("/documents")
    @Operation(summary = "Ingest document metadata", description = "Begins the modular validator-chunker-embedding pipeline")
    public ResponseEntity<AiDocumentDto> ingestDocument(
            Principal principal,
            @RequestParam("title") String title,
            @RequestParam("type") String type,
            @RequestBody String content) {
        
        AiDocument doc = AiDocument.builder()
                .id(UUID.randomUUID())
                .documentType(type.toUpperCase())
                .title(title)
                .content(content)
                .status("CREATED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        AiDocument savedDoc = documentRepository.save(doc);
        
        AiDocument processed = pipelineService.processDocument(savedDoc.getId());
        
        return ResponseEntity.ok(mapToDto(processed));
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document lifecycle status")
    public ResponseEntity<AiDocumentDto> getDocument(@PathVariable UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return ResponseEntity.ok(mapToDto(doc));
    }

    @PostMapping("/retrieval/search")
    @Operation(summary = "Perform semantic query searches")
    public ResponseEntity<Map<String, Object>> searchRetrieval(
            @RequestParam("query") String query,
            @RequestParam("type") String type) {
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("collection", type.toLowerCase() + "_vectors");

        AiTaskRequestDto request = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType("JOB_MATCH")
                .payload(payload)
                .build();

        AiTaskResponseDto response = gatewayClient.executeTask(request);
        return ResponseEntity.ok(response.getResult());
    }

    private AiDocumentDto mapToDto(AiDocument doc) {
        return AiDocumentDto.builder()
                .id(doc.getId())
                .documentType(doc.getDocumentType())
                .status(doc.getStatus())
                .ownerId(doc.getOwnerId())
                .title(doc.getTitle())
                .source(doc.getSource())
                .mimeType(doc.getMimeType())
                .language(doc.getLanguage())
                .content(doc.getContent())
                .structuredMetadata(doc.getStructuredMetadata())
                .flexibleMetadata(doc.getFlexibleMetadata())
                .version(doc.getVersion())
                .checksum(doc.getChecksum())
                .build();
    }
}
