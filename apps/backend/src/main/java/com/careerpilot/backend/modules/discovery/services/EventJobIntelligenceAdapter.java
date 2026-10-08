package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Registers each discovered job as a knowledge document so it can be analysed later.
 *
 * <p>AI analysis (parse, metadata, insights) is NOT run here: a sync can return hundreds of jobs and
 * three model calls per job would saturate the model and the DB pool inside the discovery
 * transaction. Jobs are analysed on demand ({@code POST /jobs/{id}/analyze}) and matching works on
 * connector facts until then, reporting un-assessed factors instead of guessing.
 */
@Component
public class EventJobIntelligenceAdapter implements JobIntelligencePort {
    private final AiDocumentRepository documents;

    public EventJobIntelligenceAdapter(AiDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public void accept(DiscoveryJob job) {
        AiDocument document = documents.findById(job.getId()).orElseGet(() -> AiDocument.builder()
                .id(job.getId())
                .createdAt(Instant.now())
                .build());
        boolean contentChanged = !java.util.Objects.equals(document.getContent(), job.getRawContent());
        document.setDocumentType("JOB");
        document.setTitle(job.getTitle());
        document.setSource(job.getSourceUrl());
        document.setContent(job.getRawContent());
        if (contentChanged || document.getStatus() == null) {
            // a previous analysis no longer describes this posting
            document.setStatus("CREATED");
            document.setStructuredMetadata(null);
            document.setFlexibleMetadata(Map.of("discoveryJobId", job.getId().toString(), "connectorId", job.getConnectorId()));
        }
        document.setUpdatedAt(Instant.now());
        documents.save(document);
    }
}
