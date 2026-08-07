package com.careerpilot.backend.modules.ai.resume.adapters.in.web;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeValidationReport;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeValidationReportRepository;
import com.careerpilot.backend.modules.ai.resume.services.ResumeIntelligenceService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ai/resume")
@Tag(name = "Resume Intelligence Engine", description = "Endpoints for parsing candidates CVs, skill mapping taxonomies, and scoring ATS coverage metrics")
@SecurityRequirement(name = "bearerAuth")
public class ResumeIntelligenceController {

    private final AiDocumentRepository documentRepository;
    private final ResumeValidationReportRepository validationReportRepository;
    private final ResumeIntelligenceService resumeIntelligenceService;
    private final AiGatewayClient gatewayClient;

    public ResumeIntelligenceController(
            AiDocumentRepository documentRepository,
            ResumeValidationReportRepository validationReportRepository,
            ResumeIntelligenceService resumeIntelligenceService,
            AiGatewayClient gatewayClient) {
        this.documentRepository = documentRepository;
        this.validationReportRepository = validationReportRepository;
        this.resumeIntelligenceService = resumeIntelligenceService;
        this.gatewayClient = gatewayClient;
    }

    @PostMapping("/process")
    @Operation(summary = "Process uploaded resume document", description = "Triggers section parsing, skill mapping, ATS calculations, and Qdrant indexing")
    public ResponseEntity<AiDocument> processResume(@RequestParam("documentId") UUID documentId) {
        AiDocument doc = resumeIntelligenceService.processResume(documentId);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get parsed Resume Knowledge")
    public ResponseEntity<Map<String, Object>> getResumeKnowledge(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        return ResponseEntity.ok(doc.getStructuredMetadata() != null ? doc.getStructuredMetadata() : new HashMap<>());
    }

    @GetMapping("/{id}/metadata")
    @Operation(summary = "Get structured metadata details")
    public ResponseEntity<Map<String, Object>> getResumeMetadata(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        Map<String, Object> meta = new HashMap<>();
        meta.put("checksum", doc.getChecksum());
        meta.put("version", doc.getVersion());
        meta.put("status", doc.getStatus());
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/{id}/ats")
    @Operation(summary = "Get ATS quality metrics & reports")
    public ResponseEntity<Map<String, Object>> getResumeAts(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        return ResponseEntity.ok(doc.getFlexibleMetadata() != null ? doc.getFlexibleMetadata() : new HashMap<>());
    }

    @PostMapping("/search")
    @Operation(summary = "Retrieve matching candidates by semantic skills & experience parameters")
    public ResponseEntity<Map<String, Object>> searchResumes(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("collection", "resume_vectors");

        AiTaskRequestDto request = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType("JOB_MATCH")
                .payload(payload)
                .build();

        AiTaskResponseDto response = gatewayClient.executeTask(request);
        return ResponseEntity.ok(response.getResult());
    }
}
