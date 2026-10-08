package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService.ResumeKnowledge;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.services.ResumeProcessingService;
import com.careerpilot.shared.events.ResumeAnalysisCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Ensures the candidate's resume has been analysed by the real resume-intelligence pipeline
 * (text extraction → AI parse → ATS → vector index). Never analyses placeholder text.
 */
@Service
public class ResumeAgent implements CareerAgent {

    private final ResumeRepository resumeRepository;
    private final ResumeProcessingService processingService;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeAgent(ResumeRepository resumeRepository,
                       ResumeProcessingService processingService,
                       CandidateKnowledgeService candidateKnowledgeService,
                       ApplicationEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.processingService = processingService;
        this.candidateKnowledgeService = candidateKnowledgeService;
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
        return "2.0.0";
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
        Optional<Resume> resume = resumeRepository.findDefaultByUserId(userId)
                .or(() -> resumeRepository.findActiveByUserId(userId).stream().findFirst());
        if (resume.isEmpty()) {
            return AgentResult.builder()
                    .status(AgentResult.Status.BLOCKED)
                    .message("Resume required before autonomous career search can begin.")
                    .build();
        }
        try {
            Optional<ResumeKnowledge> knowledge = candidateKnowledgeService.resumeKnowledge(userId, resume.get().getId());
            if (knowledge.isEmpty()) {
                // Not processed yet (or failed earlier): run the real pipeline now.
                Resume processed = processingService.processLatestVersion(userId, resume.get().getId());
                knowledge = candidateKnowledgeService.resumeKnowledge(userId, resume.get().getId());
                if (knowledge.isEmpty()) {
                    return AgentResult.builder()
                            .status(AgentResult.Status.FAILED)
                            .message("Resume could not be analysed: " + Objects.toString(processed.getAiProcessingError(),
                                    processed.getAiProcessingStatus()))
                            .build();
                }
            }
            ResumeKnowledge k = knowledge.get();
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("resumeId", k.resumeId().toString());
            outputData.put("documentId", k.documentId().toString());
            outputData.put("versionNumber", k.versionNumber());
            outputData.put("structuredKnowledge", k.knowledge());
            outputData.put("qualityMetrics", k.atsMetrics());

            eventPublisher.publishEvent(ResumeAnalysisCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .correlationId(context.getCorrelationId() != null ? UUID.fromString(context.getCorrelationId()) : null)
                    .userId(userId)
                    .resumeId(k.resumeId())
                    .build());
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Resume analysed (version " + k.versionNumber() + ").")
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
