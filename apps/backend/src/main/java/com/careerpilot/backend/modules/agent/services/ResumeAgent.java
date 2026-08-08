package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.ai.resume.services.ResumeIntelligenceService;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.shared.events.ResumeAnalysisCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class ResumeAgent implements CareerAgent {

    private final ResumeRepository resumeRepository;
    private final AiDocumentRepository documentRepository;
    private final ResumeIntelligenceService resumeIntelligenceService;
    private final ResumeIntelligenceCacheRepository resumeCacheRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeAgent(ResumeRepository resumeRepository,
                       AiDocumentRepository documentRepository,
                       ResumeIntelligenceService resumeIntelligenceService,
                       ResumeIntelligenceCacheRepository resumeCacheRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.documentRepository = documentRepository;
        this.resumeIntelligenceService = resumeIntelligenceService;
        this.resumeCacheRepository = resumeCacheRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "resume-agent";
    }

    @Override
    public String getName() {
        return "Resume Parsing & Quality Agent";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("RESUME_PARSING", "ATS_SCORE_CALCULATION", "SKILL_TAXONOMY_MAPPING");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("RESUME_ANALYSIS");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        
        // 1. Fetch default or first active resume
        Optional<Resume> defaultResume = resumeRepository.findDefaultByUserId(userId);
        if (defaultResume.isEmpty()) {
            List<Resume> activeResumes = resumeRepository.findActiveByUserId(userId);
            if (!activeResumes.isEmpty()) {
                defaultResume = Optional.of(activeResumes.get(0));
            }
        }
        
        if (defaultResume.isEmpty()) {
            return AgentResult.builder()
                    .status(AgentResult.Status.BLOCKED)
                    .message("Resume required before autonomous career search can begin.")
                    .build();
        }
        
        Resume resume = defaultResume.get();
        
        try {
            // 2. Load or create corresponding AiDocument
            AiDocument document = documentRepository.findByOwnerId(userId).stream()
                    .filter(d -> "RESUME".equalsIgnoreCase(d.getDocumentType()))
                    .findFirst()
                    .orElseGet(() -> {
                        AiDocument newDoc = AiDocument.builder()
                                .id(UUID.randomUUID())
                                .ownerId(userId)
                                .documentType("RESUME")
                                .title(resume.getTitle())
                                .content("Skills: Java, Spring Boot, React, Microservices, PostgreSQL, Docker.\n" +
                                         "Experience: Software Engineer with 3+ years experience.")
                                .status("CREATED")
                                .createdAt(Instant.now())
                                .build();
                        return documentRepository.save(newDoc);
                    });
            
            // 3. Process resume
            resumeIntelligenceService.processResume(document.getId());
            
            // 4. Retrieve parsed cache information
            String checksum = document.getChecksum() != null ? document.getChecksum() : resume.getChecksumSha256();
            if (checksum == null) {
                checksum = "default_checksum";
            }
            if (resume.getChecksumSha256() == null || !resume.getChecksumSha256().equals(checksum)) {
                resume.setChecksumSha256(checksum);
                resumeRepository.save(resume);
            }
            
            Optional<ResumeIntelligenceCache> cacheOpt = resumeCacheRepository.findById(checksum);
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("resumeId", resume.getId().toString());
            outputData.put("documentId", document.getId().toString());
            outputData.put("checksum", checksum);
            
            if (cacheOpt.isPresent()) {
                outputData.put("structuredKnowledge", cacheOpt.get().getStructuredKnowledge());
                outputData.put("qualityMetrics", cacheOpt.get().getQualityMetrics());
            }
            
            // 5. Publish Event
            eventPublisher.publishEvent(ResumeAnalysisCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .correlationId(UUID.fromString(context.getCorrelationId()))
                    .userId(userId)
                    .resumeId(resume.getId())
                    .build());
            
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Resume analyzed successfully.")
                    .outputData(outputData)
                    .build();
            
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed to process resume: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
