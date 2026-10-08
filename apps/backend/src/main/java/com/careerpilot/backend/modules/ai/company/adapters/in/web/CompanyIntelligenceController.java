package com.careerpilot.backend.modules.ai.company.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.ai.company.services.CompanyIntelligenceService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.web.SafeWebPageFetcher;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Company intelligence built only from real sources: the job posting's own text and public pages the
 * user explicitly provides. The model is told not to use outside knowledge; facts are grounded.
 */
@RestController
@RequestMapping("/api/v1/ai/company")
@Tag(name = "Company Intelligence Engine", description = "Company analysis and research from real sources")
@SecurityRequirement(name = "bearerAuth")
public class CompanyIntelligenceController {

    private static final int MAX_RESEARCH_URLS = 3;

    private final AiDocumentRepository documentRepository;
    private final CompanyIntelligenceService companyIntelligenceService;
    private final AiGatewayClient gatewayClient;
    private final JobContextService jobContextService;
    private final SafeWebPageFetcher webPageFetcher;
    private final CurrentUser currentUser;

    public CompanyIntelligenceController(AiDocumentRepository documentRepository,
                                         CompanyIntelligenceService companyIntelligenceService,
                                         AiGatewayClient gatewayClient,
                                         JobContextService jobContextService,
                                         SafeWebPageFetcher webPageFetcher,
                                         CurrentUser currentUser) {
        this.documentRepository = documentRepository;
        this.companyIntelligenceService = companyIntelligenceService;
        this.gatewayClient = gatewayClient;
        this.jobContextService = jobContextService;
        this.webPageFetcher = webPageFetcher;
        this.currentUser = currentUser;
    }

    @PostMapping("/process-url")
    @Operation(summary = "Fetch a public company page and analyse it with AI")
    public ResponseEntity<Map<String, Object>> processCompanyUrl(@RequestParam("url") String url, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        return ResponseEntity.ok(view(companyIntelligenceService.processCompanyUrl(userId, url)));
    }

    /**
     * Research the company behind a discovered job, using the posting text plus up to three public
     * URLs the user supplies (e.g. the company's careers or engineering blog page).
     */
    @PostMapping("/research")
    @Operation(summary = "AI company research summary from the job posting and user-supplied public pages")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> research(@RequestBody Map<String, Object> body, Principal principal) {
        currentUser.requireId(principal);
        Object jobIdRaw = body.get("jobId");
        if (jobIdRaw == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        DiscoveryJob job = jobContextService.requireJob(UUID.fromString(String.valueOf(jobIdRaw)));
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(Map.of("source", "job-posting:" + job.getConnectorId(), "text", jobContextService.jobText(job)));
        List<Map<String, String>> fetchErrors = new ArrayList<>();
        List<String> urls = body.get("urls") instanceof List<?> l ? (List<String>) l : List.of();
        for (String url : urls.stream().limit(MAX_RESEARCH_URLS).toList()) {
            try {
                SafeWebPageFetcher.FetchedPage page = webPageFetcher.fetch(url);
                String text = page.text().length() > 8000 ? page.text().substring(0, 8000) : page.text();
                sources.add(Map.of("source", page.finalUrl(), "text", text));
            } catch (IllegalArgumentException | IllegalStateException e) {
                fetchErrors.add(Map.of("url", url, "error", e.getMessage()));
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("sources", sources);
        payload.put("context", Map.of("company", Objects.toString(job.getCompany(), "unknown"),
                "role", Objects.toString(job.getTitle(), "unknown")));
        Map<String, Object> result = new LinkedHashMap<>(gatewayClient.run("COMPANY_RESEARCH_SUMMARY", payload));
        result.put("company", job.getCompany());
        result.put("jobId", job.getId());
        result.put("fetchErrors", fetchErrors);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCompany(@PathVariable("id") UUID id, Principal principal) {
        return ResponseEntity.ok(view(readable(id, currentUser.requireId(principal))));
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<Object> getCompanyMetadata(@PathVariable("id") UUID id, Principal principal) {
        return ResponseEntity.ok(flexible(readable(id, currentUser.requireId(principal)), "metadata"));
    }

    @GetMapping("/{id}/insights")
    public ResponseEntity<Object> getCompanyInsights(@PathVariable("id") UUID id, Principal principal) {
        return ResponseEntity.ok(flexible(readable(id, currentUser.requireId(principal)), "insights"));
    }

    @PostMapping("/search")
    @Operation(summary = "Semantic search over analysed company documents")
    public ResponseEntity<Map<String, Object>> searchCompanies(@RequestBody Map<String, Object> body, Principal principal) {
        currentUser.requireId(principal);
        String query = body.get("query") instanceof String q ? q.trim() : "";
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query is required");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("documentType", "COMPANY");
        payload.put("limit", body.get("limit") instanceof Number n ? n.intValue() : 5);
        return ResponseEntity.ok(gatewayClient.run("COMPANY_SEARCH", payload));
    }

    private AiDocument readable(UUID id, UUID userId) {
        AiDocument doc = documentRepository.findById(id)
                .filter(d -> "COMPANY".equalsIgnoreCase(d.getDocumentType()))
                .orElseThrow(() -> new NoSuchElementException("Company not found"));
        if (doc.getOwnerId() != null && !doc.getOwnerId().equals(userId)) {
            throw new NoSuchElementException("Company not found");
        }
        return doc;
    }

    private static Object flexible(AiDocument doc, String key) {
        Map<String, Object> flexible = doc.getFlexibleMetadata();
        Object value = flexible != null ? flexible.get(key) : null;
        return value != null ? value : Map.of();
    }

    private static Map<String, Object> view(AiDocument doc) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("documentId", doc.getId());
        view.put("status", doc.getStatus());
        view.put("title", doc.getTitle());
        view.put("source", doc.getSource());
        view.put("knowledge", doc.getStructuredMetadata());
        view.put("metadata", flexible(doc, "metadata"));
        view.put("insights", flexible(doc, "insights"));
        return view;
    }
}
