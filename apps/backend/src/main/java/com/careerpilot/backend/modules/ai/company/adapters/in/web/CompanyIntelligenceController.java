package com.careerpilot.backend.modules.ai.company.adapters.in.web;

import com.careerpilot.backend.modules.ai.company.services.CompanyIntelligenceService;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.careerpilot.shared.dto.ai.company.CompanySearchQueryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ai/company")
@Tag(name = "Company Intelligence Engine", description = "Endpoints for parsing company information, analyzing tech stacks, and retrieving engineering profiles")
@SecurityRequirement(name = "bearerAuth")
public class CompanyIntelligenceController {

    private final AiDocumentRepository documentRepository;
    private final CompanyIntelligenceService companyIntelligenceService;
    private final AiGatewayClient gatewayClient;

    public CompanyIntelligenceController(
            AiDocumentRepository documentRepository,
            CompanyIntelligenceService companyIntelligenceService,
            AiGatewayClient gatewayClient) {
        this.documentRepository = documentRepository;
        this.companyIntelligenceService = companyIntelligenceService;
        this.gatewayClient = gatewayClient;
    }

    @PostMapping("/process")
    @Operation(summary = "Process ingested company content", description = "Triggers crawler parsing, canonical domain mapping, categorized insights, and chunk indexing")
    public ResponseEntity<AiDocument> processCompany(
            @RequestParam("documentId") UUID documentId,
            @RequestParam(value = "url", required = false) String url) {
        AiDocument doc = companyIntelligenceService.processCompany(documentId, null, url);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/process-url")
    @Operation(summary = "Initiates website crawl parsing", description = "Crawl parsing triggers complete pipeline from external company URL links")
    public ResponseEntity<AiDocument> processCompanyUrl(
            @RequestParam("ownerId") UUID ownerId,
            @RequestParam("url") String url) {
        AiDocument doc = companyIntelligenceService.processCompanyUrl(ownerId, url);
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get parsed Company Knowledge details")
    public ResponseEntity<Map<String, Object>> getCompanyKnowledge(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        return ResponseEntity.ok(doc.getStructuredMetadata() != null ? doc.getStructuredMetadata() : new HashMap<>());
    }

    @GetMapping("/{id}/metadata")
    @Operation(summary = "Get company metadata taxonomy metrics")
    public ResponseEntity<Map<String, Object>> getCompanyMetadata(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
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
    public ResponseEntity<Map<String, Object>> getCompanyInsights(@PathVariable("id") UUID id) {
        AiDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
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
    @Operation(summary = "Retrieve matching company profiles by semantic technologies and size filters")
    public ResponseEntity<Map<String, Object>> searchCompanies(@RequestBody CompanySearchQueryDto queryDto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", queryDto.getQuery());
        payload.put("collection", "company_vectors");
        payload.put("industryFilter", queryDto.getIndustryFilter());
        payload.put("technologyFilter", queryDto.getTechnologyFilter());
        payload.put("locationFilter", queryDto.getLocationFilter());
        payload.put("companySizeFilter", queryDto.getCompanySizeFilter());
        payload.put("remotePolicyFilter", queryDto.getRemotePolicyFilter());
        payload.put("page", queryDto.getPage() != null ? queryDto.getPage() : 1);
        payload.put("size", queryDto.getSize() != null ? queryDto.getSize() : 5);
        payload.put("scoreThreshold", queryDto.getScoreThreshold() != null ? queryDto.getScoreThreshold() : 0.0);

        AiTaskRequestDto request = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType("COMPANY_SEARCH")
                .payload(payload)
                .build();

        AiTaskResponseDto response = gatewayClient.executeTask(request);
        return ResponseEntity.ok(response.getResult());
    }
}
