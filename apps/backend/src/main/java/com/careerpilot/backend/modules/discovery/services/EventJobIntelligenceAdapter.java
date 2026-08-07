package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.backend.modules.ai.job.services.JobIntelligenceService;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Bridges discovery into the existing intelligence pipeline; matching remains downstream of its knowledge events. */
@Component
public class EventJobIntelligenceAdapter implements JobIntelligencePort {
    private final AiDocumentRepository documents;
    private final JobIntelligenceService intelligence;

    public EventJobIntelligenceAdapter(AiDocumentRepository documents, JobIntelligenceService intelligence) {
        this.documents = documents;
        this.intelligence = intelligence;
    }

    @Override
    public void accept(DiscoveryJob job) {
        AiDocument document = AiDocument.builder()
                .id(job.getId())
                .documentType("JOB")
                .status("CREATED")
                .title(job.getTitle())
                .source(job.getSourceUrl())
                .content(job.getRawContent())
                .flexibleMetadata(Map.of("discoveryJobId", job.getId().toString(), "connectorId", job.getConnectorId()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        documents.save(document);
        intelligence.processJob(document.getId(), job.getRawContent(), job.getSourceUrl());
    }
}
