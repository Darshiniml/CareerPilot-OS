package com.careerpilot.backend.modules.ai.job.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.ai.job.services.JobIntelligenceService;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Job intelligence. Discovered jobs are shared public data; documents a user submitted (pasted text
 * or a URL) are readable only by that user. The owner always comes from the authenticated principal.
 */
@RestController
@RequestMapping("/api/v1/ai/job")
@Tag(name = "Job Intelligence Engine", description = "AI job analysis, metadata, insights and semantic job search")
@SecurityRequirement(name = "bearerAuth")
public class JobIntelligenceController {

    private final AiDocumentRepository documentRepository;
    private final JobIntelligenceService jobIntelligenceService;
    private final JobContextService jobContextService;
    private final DiscoveryJobRepository discoveryJobRepository;
    private final AiGatewayClient gatewayClient;
    private final CurrentUser currentUser;

    public JobIntelligenceController(AiDocumentRepository documentRepository,
                                     JobIntelligenceService jobIntelligenceService,
                                     JobContextService jobContextService,
                                     DiscoveryJobRepository discoveryJobRepository,
                                     AiGatewayClient gatewayClient,
                                     CurrentUser currentUser) {
        this.documentRepository = documentRepository;
        this.jobIntelligenceService = jobIntelligenceService;
        this.jobContextService = jobContextService;
        this.discoveryJobRepository = discoveryJobRepository;
        this.gatewayClient = gatewayClient;
        this.currentUser = currentUser;
    }

    @PostMapping("/process-url")
    @Operation(summary = "Fetch a public job posting URL and analyse it with AI")
    public ResponseEntity<Map<String, Object>> processJobUrl(@RequestParam("url") String url, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        AiDocument doc = jobIntelligenceService.processJobUrl(userId, url);
        return ResponseEntity.ok(view(doc));
    }

    @PostMapping("/process-text")
    @Operation(summary = "Analyse a pasted job description with AI")
    public ResponseEntity<Map<String, Object>> processJobText(@RequestBody Map<String, String> body, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        String text = body.get("content");
        if (text == null || text.split("\\s+").length < 15) {
            throw new IllegalArgumentException("Paste the full job description (at least a few sentences)");
        }
        AiDocument doc = documentRepository.save(AiDocument.builder()
                .id(UUID.randomUUID())
                .documentType("JOB")
                .status("CREATED")
                .ownerId(userId)
                .title(Optional.ofNullable(body.get("title")).filter(t -> !t.isBlank()).orElse("Pasted job description"))
                .content(text)
                .build());
        return ResponseEntity.ok(view(jobIntelligenceService.processJob(doc.getId(), text, null)));
    }

    @PostMapping("/{jobId}/analyze")
    @Operation(summary = "Run AI analysis (parse, metadata, insights) for a discovered job")
    public ResponseEntity<Map<String, Object>> analyzeDiscoveredJob(@PathVariable UUID jobId, Principal principal) {
        currentUser.requireId(principal);
        return ResponseEntity.ok(view(jobContextService.analyze(jobId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Job details: connector data plus AI analysis when available")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable("id") UUID id, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        Optional<DiscoveryJob> discovered = discoveryJobRepository.findById(id);
        Map<String, Object> response = new LinkedHashMap<>();
        if (discovered.isPresent()) {
            response.putAll(jobContextService.jobCard(discovered.get()));
            jobContextService.analysis(id).ifPresentOrElse(
                    doc -> response.put("analysis", view(doc)),
                    () -> response.put("analysis", null));
            return ResponseEntity.ok(response);
        }
        AiDocument doc = readableJobDocument(id, userId);
        return ResponseEntity.ok(view(doc));
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<Object> getJobMetadata(@PathVariable("id") UUID id, Principal principal) {
        AiDocument doc = readableJobDocument(id, currentUser.requireId(principal));
        return ResponseEntity.ok(flexible(doc, "metadata"));
    }

    @GetMapping("/{id}/insights")
    public ResponseEntity<Object> getJobInsights(@PathVariable("id") UUID id, Principal principal) {
        AiDocument doc = readableJobDocument(id, currentUser.requireId(principal));
        return ResponseEntity.ok(flexible(doc, "insights"));
    }

    /**
     * Search discovered jobs. With a query: semantic vector search over real job postings. Without a
     * query: the most recently discovered jobs.
     */
    @PostMapping("/search")
    @Operation(summary = "Semantic search over discovered jobs")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> searchJobs(@RequestBody(required = false) Map<String, Object> body,
                                                          Principal principal) {
        currentUser.requireId(principal);
        Map<String, Object> request = body != null ? body : Map.of();
        String query = request.get("query") instanceof String q ? q.trim() : "";
        int limit = Math.max(1, Math.min(50, request.get("limit") instanceof Number n ? n.intValue()
                : request.get("size") instanceof Number s ? s.intValue() : 20));

        List<Map<String, Object>> results = new ArrayList<>();
        if (query.isEmpty()) {
            discoveryJobRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "discoveredAt")))
                    .forEach(job -> results.add(jobContextService.jobCard(job)));
            return ResponseEntity.ok(Map.of("results", results, "mode", "recent"));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("documentType", "JOB");
        payload.put("limit", limit);
        Map<String, Object> result = gatewayClient.run("JOB_SEARCH", payload);
        Set<UUID> seen = new LinkedHashSet<>();
        for (Map<String, Object> hit : (List<Map<String, Object>>) result.getOrDefault("results", List.of())) {
            UUID jobId;
            try {
                jobId = UUID.fromString(String.valueOf(hit.get("documentId")));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!seen.add(jobId)) {
                continue;
            }
            discoveryJobRepository.findById(jobId).ifPresent(job -> {
                Map<String, Object> card = new LinkedHashMap<>(jobContextService.jobCard(job));
                card.put("relevance", hit.get("score"));
                card.put("matchedText", hit.get("text"));
                results.add(card);
            });
        }
        return ResponseEntity.ok(Map.of("results", results, "mode", "semantic"));
    }

    private AiDocument readableJobDocument(UUID id, UUID userId) {
        AiDocument doc = documentRepository.findById(id)
                .filter(d -> "JOB".equalsIgnoreCase(d.getDocumentType()))
                .orElseThrow(() -> new NoSuchElementException("Job not found"));
        if (doc.getOwnerId() != null && !doc.getOwnerId().equals(userId)) {
            throw new NoSuchElementException("Job not found"); // do not reveal other users' documents exist
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
        view.put("qualityMetrics", flexible(doc, "qualityMetrics"));
        view.put("insights", flexible(doc, "insights"));
        view.put("updatedAt", doc.getUpdatedAt());
        return view;
    }
}
