package com.careerpilot.backend.modules.ai.job.services;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.web.SafeWebPageFetcher;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Real job context for AI features, built only from the discovered job record (connector data with
 * provenance) and, when the job has been analysed, its validated parsed knowledge.
 *
 * <p>Fields a connector could not verify stay absent; nothing is filled with defaults.</p>
 */
@Service
@RequiredArgsConstructor
public class JobContextService {

    static final int MAX_DESCRIPTION_CHARS = 20000;

    private final DiscoveryJobRepository jobRepository;
    private final AiDocumentRepository documentRepository;
    private final JobIntelligenceService jobIntelligenceService;
    private final com.careerpilot.backend.modules.ai.matching.MatchingCache matchingCache;

    @Transactional(readOnly = true)
    public DiscoveryJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new NoSuchElementException("Job not found"));
    }

    /** Connector-provided facts about the job, labelled with their source. */
    public Map<String, Object> jobCard(DiscoveryJob job) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("jobId", job.getId());
        put(card, "title", job.getTitle());
        put(card, "company", job.getCompany());
        put(card, "location", job.getLocation());
        put(card, "workMode", job.getWorkMode());
        put(card, "employmentType", job.getEmploymentType());
        put(card, "salary", job.getSalary());
        put(card, "postedDate", job.getPostedDate());
        put(card, "sourceUrl", job.getSourceUrl());
        put(card, "source", job.getSource());
        put(card, "connectorId", job.getConnectorId());
        put(card, "discoveredAt", job.getDiscoveredAt());
        put(card, "lastSeenAt", job.getLastSeenAt());
        card.put("provenance", "connector:" + job.getConnectorId());
        return card;
    }

    /** Plain-text job description: connector header facts + description with HTML removed. */
    public String jobText(DiscoveryJob job) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Job title", job.getTitle());
        line(sb, "Company", job.getCompany());
        line(sb, "Location", job.getLocation());
        line(sb, "Work mode", job.getWorkMode());
        line(sb, "Employment type", job.getEmploymentType());
        line(sb, "Salary", job.getSalary());
        sb.append('\n');
        String description = job.getRawContent() == null ? "" : job.getRawContent();
        if (description.contains("<") && description.contains(">")) {
            description = SafeWebPageFetcher.htmlToText(description);
        }
        sb.append(description.trim());
        String text = sb.toString().trim();
        return text.length() > MAX_DESCRIPTION_CHARS ? text.substring(0, MAX_DESCRIPTION_CHARS) : text;
    }

    /** Parsed knowledge for an analysed job (AI document id == discovered job id). */
    @Transactional(readOnly = true)
    public Optional<AiDocument> analysis(UUID jobId) {
        return documentRepository.findById(jobId)
                .filter(d -> "JOB".equalsIgnoreCase(d.getDocumentType()))
                .filter(d -> "READY".equals(d.getStatus()));
    }

    /** Run AI job analysis (parse + metadata + insights + vector index) for a discovered job.
     *  Deliberately not transactional so no DB connection is held during the model calls. */
    public AiDocument analyze(UUID jobId) {
        DiscoveryJob job = requireJob(jobId);
        String text = jobText(job);
        if (text.split("\\s+").length < 15) {
            throw new IllegalStateException("This job has too little description text to analyse");
        }
        AiDocument doc = documentRepository.findById(jobId).orElseGet(() -> AiDocument.builder()
                .id(jobId)
                .createdAt(Instant.now())
                .build());
        doc.setDocumentType("JOB");
        doc.setOwnerId(null); // discovered jobs are shared public data
        doc.setTitle(job.getTitle() != null ? job.getTitle() : "Job " + jobId);
        doc.setSource(job.getSourceUrl());
        doc.setContent(text);
        doc.setStatus("CREATED");
        doc.setStructuredMetadata(null);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
        AiDocument analysed = jobIntelligenceService.processJob(jobId, text, job.getSourceUrl());
        matchingCache.evictJob(jobId); // cached matches were computed without the parsed requirements
        return analysed;
    }

    /** Compact job context for model prompts: connector facts + parsed requirements when available. */
    @Transactional(readOnly = true)
    public Map<String, Object> jobContext(UUID jobId) {
        DiscoveryJob job = requireJob(jobId);
        Map<String, Object> ctx = new LinkedHashMap<>(jobCard(job));
        analysis(jobId).ifPresentOrElse(
                doc -> ctx.put("parsedRequirements", doc.getStructuredMetadata()),
                () -> ctx.put("description", jobText(job)));
        return ctx;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            map.put(key, value);
        }
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value.trim()).append('\n');
        }
    }
}
