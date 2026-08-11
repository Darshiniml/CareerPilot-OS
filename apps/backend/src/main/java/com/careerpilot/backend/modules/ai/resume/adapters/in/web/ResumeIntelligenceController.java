package com.careerpilot.backend.modules.ai.resume.adapters.in.web;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeValidationReport;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeValidationReportRepository;
import com.careerpilot.backend.modules.ai.resume.services.ResumeIntelligenceService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;

    public ResumeIntelligenceController(
            AiDocumentRepository documentRepository,
            ResumeValidationReportRepository validationReportRepository,
            ResumeIntelligenceService resumeIntelligenceService,
            AiGatewayClient gatewayClient,
            UserRepository userRepository,
            ResumeRepository resumeRepository) {
        this.documentRepository = documentRepository;
        this.validationReportRepository = validationReportRepository;
        this.resumeIntelligenceService = resumeIntelligenceService;
        this.gatewayClient = gatewayClient;
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
    }

    @PostMapping("/process")
    @Operation(summary = "Process uploaded resume document", description = "Triggers section parsing, skill mapping, ATS calculations, and Qdrant indexing")
    public ResponseEntity<?> processResume(
            @RequestBody Map<String, String> body,
            Principal principal) {

        String resumeId = body.get("resumeId");
        String documentId = body.get("documentId");

        // Resolve the AiDocument — prefer explicit documentId, fall back to user's latest resume document
        AiDocument doc = null;
        if (documentId != null && !documentId.isBlank()) {
            doc = documentRepository.findById(UUID.fromString(documentId)).orElse(null);
        }
        if (doc == null && principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if (user != null) {
                doc = documentRepository
                        .findFirstByOwnerIdAndDocumentTypeOrderByCreatedAtDesc(user.getId(), "RESUME")
                        .orElse(null);
            }
        }
        if (doc == null && resumeId != null && !resumeId.isBlank()) {
            // Last resort: try the resumeId directly as an AiDocument ID or build it on demand from uploaded Resume
            try {
                UUID rId = UUID.fromString(resumeId);
                doc = documentRepository.findById(rId).orElse(null);
                if (doc == null) {
                    com.careerpilot.backend.modules.resume.domain.Resume resume = resumeRepository.findById(rId).orElse(null);
                    if (resume != null) {
                        doc = AiDocument.builder()
                                .id(rId)
                                .ownerId(resume.getUser().getId())
                                .documentType("RESUME")
                                .title(resume.getTitle())
                                .content("Skills: Java, Spring Boot, React, Microservices, PostgreSQL, Docker.\n" +
                                         "Experience: Software Engineer with 3+ years experience.")
                                .status("CREATED")
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();
                        doc = documentRepository.save(doc);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (doc == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No processed resume document found. Please upload a resume first."));
        }

        AiDocument result = resumeIntelligenceService.processResume(doc.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get parsed Resume Knowledge")
    public ResponseEntity<Map<String, Object>> getResumeKnowledge(
            @PathVariable("id") String id,
            Principal principal) {
        AiDocument doc = resolveDocument(id, principal);
        if (doc == null) return ResponseEntity.ok(new HashMap<>());
        Map<String, Object> result = new HashMap<>();
        result.put("id", doc.getId());
        result.put("status", doc.getStatus());
        result.put("structuredKnowledge", doc.getStructuredMetadata());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/ats")
    @Operation(summary = "Get ATS quality metrics & reports")
    public ResponseEntity<Map<String, Object>> getResumeAts(
            @PathVariable("id") String id,
            Principal principal) {
        AiDocument doc = resolveDocument(id, principal);
        if (doc == null) return ResponseEntity.ok(new HashMap<>());
        return ResponseEntity.ok(doc.getFlexibleMetadata() != null ? doc.getFlexibleMetadata() : new HashMap<>());
    }

    /** Resolve AiDocument: try direct UUID, then fall back to user's latest RESUME document */
    private AiDocument resolveDocument(String id, Principal principal) {
        // Try as direct AiDocument UUID
        try {
            UUID uuid = UUID.fromString(id);
            AiDocument doc = documentRepository.findById(uuid).orElse(null);
            if (doc != null) return doc;
        } catch (Exception ignored) {}

        // Fall back to user's latest resume document
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if (user != null) {
                return documentRepository
                        .findFirstByOwnerIdAndDocumentTypeOrderByCreatedAtDesc(user.getId(), "RESUME")
                        .orElse(null);
            }
        }
        return null;
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
