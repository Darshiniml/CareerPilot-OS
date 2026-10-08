package com.careerpilot.backend.modules.ai.resume.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService.ResumeKnowledge;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.services.ResumeProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Resume intelligence for the authenticated candidate only. Every lookup is scoped to the caller's
 * own resumes; there is no cross-candidate access and no placeholder resume text.
 */
@RestController
@RequestMapping("/api/v1/ai/resume")
@Tag(name = "Resume Intelligence Engine", description = "AI resume parsing, ATS analysis, semantic search and job-specific optimisation")
@SecurityRequirement(name = "bearerAuth")
public class ResumeIntelligenceController {

    private final ResumeRepository resumeRepository;
    private final ResumeProcessingService processingService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final JobContextService jobContextService;
    private final AiGatewayClient gatewayClient;
    private final CurrentUser currentUser;

    public ResumeIntelligenceController(ResumeRepository resumeRepository,
                                        ResumeProcessingService processingService,
                                        CandidateKnowledgeService candidateKnowledgeService,
                                        JobContextService jobContextService,
                                        AiGatewayClient gatewayClient,
                                        CurrentUser currentUser) {
        this.resumeRepository = resumeRepository;
        this.processingService = processingService;
        this.candidateKnowledgeService = candidateKnowledgeService;
        this.jobContextService = jobContextService;
        this.gatewayClient = gatewayClient;
        this.currentUser = currentUser;
    }

    /** Re-run text extraction + AI analysis for one of the caller's resumes (asynchronous). */
    @PostMapping("/process")
    @Operation(summary = "(Re)process a resume: extract text, AI parse, ATS analysis, vector index")
    public ResponseEntity<Map<String, Object>> processResume(@RequestBody Map<String, String> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        UUID resumeId = parseId(body.get("resumeId"), "resumeId");
        Resume resume = ownedResume(userId, resumeId);
        CompletableFuture.runAsync(() -> {
            try {
                processingService.processLatestVersion(userId, resumeId);
            } catch (RuntimeException ignored) {
                // status and error are recorded on the resume
            }
        });
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "resumeId", resume.getId(), "aiProcessingStatus", "PROCESSING"));
    }

    /** Lightweight summary used by the dashboard / career-search profile. */
    @GetMapping("/parse-summary")
    public ResponseEntity<Map<String, Object>> parseSummary(@RequestParam(value = "resumeId", required = false) String resumeId,
                                                            Principal principal) {
        UUID userId = currentUser.requireId(principal);
        Optional<ResumeKnowledge> knowledge = resumeId == null || resumeId.isBlank()
                ? candidateKnowledgeService.primaryResume(userId)
                : candidateKnowledgeService.resumeKnowledge(userId, parseId(resumeId, "resumeId"));
        if (knowledge.isEmpty()) {
            return ResponseEntity.ok(Map.of("available", false));
        }
        Map<String, Object> k = knowledge.get().knowledge();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("available", true);
        summary.put("resumeId", knowledge.get().resumeId());
        summary.put("versionNumber", knowledge.get().versionNumber());
        summary.put("skills", k.getOrDefault("skills", List.of()));
        summary.put("experience", k.getOrDefault("experience", List.of()));
        summary.put("education", k.getOrDefault("education", List.of()));
        summary.put("intelligence", k.getOrDefault("intelligence", Map.of()));
        summary.put("atsScore", knowledge.get().atsMetrics().get("atsScore"));
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "Parsed knowledge of the newest processed version of one of my resumes")
    public ResponseEntity<Map<String, Object>> getResumeKnowledge(@PathVariable("resumeId") UUID resumeId, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        Resume resume = ownedResume(userId, resumeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resumeId", resumeId);
        result.put("aiProcessingStatus", resume.getAiProcessingStatus());
        result.put("processingError", resume.getAiProcessingError());
        candidateKnowledgeService.resumeKnowledge(userId, resumeId).ifPresentOrElse(k -> {
            result.put("documentId", k.documentId());
            result.put("versionNumber", k.versionNumber());
            result.put("structuredKnowledge", k.knowledge());
        }, () -> result.put("structuredKnowledge", null));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{resumeId}/ats")
    @Operation(summary = "ATS metrics and AI review for one of my resumes")
    public ResponseEntity<Map<String, Object>> getResumeAts(@PathVariable("resumeId") UUID resumeId, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        ownedResume(userId, resumeId);
        return ResponseEntity.ok(candidateKnowledgeService.resumeKnowledge(userId, resumeId)
                .map(ResumeKnowledge::atsMetrics)
                .orElse(Map.of()));
    }

    /** Semantic search across the caller's OWN resume content (never other candidates). */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMyResumes(@RequestBody Map<String, Object> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        String query = body.get("query") instanceof String q ? q.trim() : "";
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query is required");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("documentType", "RESUME");
        payload.put("ownerId", userId.toString());
        payload.put("limit", body.get("limit") instanceof Number n ? n.intValue() : 5);
        return ResponseEntity.ok(gatewayClient.run("RESUME_SEARCH", payload));
    }

    /** Job-specific optimisation suggestions. Rewording only; invented claims are flagged. */
    @PostMapping("/{resumeId}/optimize")
    @Operation(summary = "Tailor one of my resumes to a specific job")
    public ResponseEntity<Map<String, Object>> optimize(@PathVariable("resumeId") UUID resumeId,
                                                        @RequestBody Map<String, String> body,
                                                        Principal principal) {
        UUID userId = currentUser.requireId(principal);
        ownedResume(userId, resumeId);
        ResumeKnowledge knowledge = candidateKnowledgeService.resumeKnowledge(userId, resumeId)
                .orElseThrow(() -> new IllegalStateException("This resume has not been processed yet"));
        DiscoveryJob job = jobContextService.requireJob(parseId(body.get("jobId"), "jobId"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("resumeText", knowledge.text());
        payload.put("jobDescription", jobContextService.jobText(job));
        payload.put("context", Map.of("targetRole", Objects.toString(job.getTitle(), ""),
                "targetCompany", Objects.toString(job.getCompany(), "")));
        Map<String, Object> result = new LinkedHashMap<>(gatewayClient.run("RESUME_OPTIMIZE", payload));
        result.put("resumeId", resumeId);
        result.put("resumeVersion", knowledge.versionNumber());
        result.put("jobId", job.getId());
        return ResponseEntity.ok(result);
    }

    private Resume ownedResume(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findActiveById(resumeId)
                .orElseThrow(() -> new NoSuchElementException("Resume not found"));
        if (!resume.getUser().getId().equals(userId)) {
            throw new NoSuchElementException("Resume not found");
        }
        return resume;
    }

    private static UUID parseId(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be a valid id");
        }
    }
}
