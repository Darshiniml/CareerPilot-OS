package com.careerpilot.backend.modules.ai.job.adapters.in.web;

import com.careerpilot.backend.modules.ai.job.services.JobIntelligenceService;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.careerpilot.shared.dto.ai.job.JobSearchQueryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ai/job")
@Tag(name = "Job Intelligence Engine", description = "Endpoints for parsing jobs, analyzing tech stacks, and retrieving engineering profiles")
@SecurityRequirement(name = "bearerAuth")
public class JobIntelligenceController {

    private final AiDocumentRepository documentRepository;
    private final JobIntelligenceService jobIntelligenceService;
    private final AiGatewayClient gatewayClient;

    public JobIntelligenceController(
            AiDocumentRepository documentRepository,
            JobIntelligenceService jobIntelligenceService,
            AiGatewayClient gatewayClient) {
        this.documentRepository = documentRepository;
        this.jobIntelligenceService = jobIntelligenceService;
        this.gatewayClient = gatewayClient;
    }

    @PostMapping("/process")
    @Operation(summary = "Process ingested job description content", description = "Triggers parser normalization, duplicate listing checks, and semantic embedding")
    public ResponseEntity<AiDocument> processJob(
            @RequestParam("documentId") UUID documentId,
            @RequestParam(value = "url", required = false) String url) {
        AiDocument doc = jobIntelligenceService.processJob(documentId, null, url);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/process-url")
    @Operation(summary = "Initiates external website job crawl parsing")
    public ResponseEntity<AiDocument> processJobUrl(
            @RequestParam("ownerId") UUID ownerId,
            @RequestParam("url") String url) {
        AiDocument doc = jobIntelligenceService.processJobUrl(ownerId, url);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get parsed Job Knowledge details")
    public ResponseEntity<Map<String, Object>> getJobKnowledge(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        return ResponseEntity.ok(doc.getStructuredMetadata() != null ? doc.getStructuredMetadata() : new HashMap<>());
    }

    @GetMapping("/{id}/metadata")
    @Operation(summary = "Get job metadata taxonomy metrics")
    public ResponseEntity<Map<String, Object>> getJobMetadata(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        Map<String, Object> response = new HashMap<>();
        if (doc.getFlexibleMetadata() != null && doc.getFlexibleMetadata().containsKey("metadata")) {
            Object meta = doc.getFlexibleMetadata().get("metadata");
            if (meta instanceof Map) {
                return ResponseEntity.ok((Map<String, Object>) meta);
            }
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/insights")
    @Operation(summary = "Get categorized business and engineering insights")
    public ResponseEntity<Map<String, Object>> getJobInsights(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        Map<String, Object> response = new HashMap<>();
        if (doc.getFlexibleMetadata() != null && doc.getFlexibleMetadata().containsKey("insights")) {
            Object insights = doc.getFlexibleMetadata().get("insights");
            if (insights instanceof Map) {
                return ResponseEntity.ok((Map<String, Object>) insights);
            }
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search")
    @Operation(summary = "Retrieve matching jobs by semantic filters")
    public ResponseEntity<Map<String, Object>> searchJobs(@RequestBody JobSearchQueryDto queryDto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", queryDto.getQuery());
        payload.put("collection", "job_vectors");
        payload.put("industryFilter", queryDto.getIndustryFilter());
        payload.put("technologyFilter", queryDto.getTechnologyFilter());
        payload.put("locationFilter", queryDto.getLocationFilter());
        payload.put("companySizeFilter", queryDto.getCompanySizeFilter());
        payload.put("remotePolicyFilter", queryDto.getRemotePolicyFilter());
        payload.put("employmentType", queryDto.getEmploymentType());
        payload.put("seniority", queryDto.getSeniority());
        payload.put("roleFamily", queryDto.getRoleFamily());
        payload.put("workMode", queryDto.getWorkMode());
        payload.put("experienceMin", queryDto.getExperienceMin());
        payload.put("experienceMax", queryDto.getExperienceMax());
        payload.put("page", queryDto.getPage() != null ? queryDto.getPage() : 1);
        payload.put("size", queryDto.getSize() != null ? queryDto.getSize() : 5);
        payload.put("scoreThreshold", queryDto.getScoreThreshold() != null ? queryDto.getScoreThreshold() : 0.0);

        AiTaskRequestDto request = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType("JOB_SEARCH")
                .payload(payload)
                .build();

        AiTaskResponseDto response = gatewayClient.executeTask(request);
        return ResponseEntity.ok(response.getResult());
    }
}
