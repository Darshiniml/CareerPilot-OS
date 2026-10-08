package com.careerpilot.backend.modules.resume.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.ai.resume.services.ResumeIntelligenceService;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.backend.modules.storage.StorageService;
import com.careerpilot.shared.events.ResumeUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns an uploaded resume file into resume intelligence:
 * download → extract text → AI document (owned by the candidate) → AI parse + ATS → vector index.
 *
 * <p>Runs asynchronously after the upload transaction commits (local models can take minutes).
 * Progress is visible through {@code Resume.aiProcessingStatus}: PENDING → PROCESSING → READY, or
 * FAILED / NO_TEXT_LAYER with an explicit {@code aiProcessingError}. Nothing is invented when a step
 * fails. Each resume version gets its own AI document whose id equals the version id, so versions
 * remain traceable and the original upload is never overwritten.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeProcessingService {

    private static final int MAX_TEXT_CHARS = 24000;

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final StorageService storageService;
    private final AiGatewayClient gatewayClient;
    private final AiDocumentRepository documentRepository;
    private final ResumeIntelligenceService resumeIntelligenceService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeUploaded(ResumeUploadedEvent event) {
        try {
            processLatestVersion(event.getUserId(), event.getResumeId());
        } catch (RuntimeException e) {
            // already recorded on the resume by processLatestVersion
            log.warn("Resume {} processing ended with error: {}", event.getResumeId(), e.getMessage());
        }
    }

    /** Process (or re-process) the newest version of a resume owned by {@code userId}. */
    public Resume processLatestVersion(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findActiveById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Active resume not found"));
        if (!resume.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to resume");
        }
        List<ResumeVersion> versions = versionRepository.findByResumeIdOrderByVersionNumberDesc(resumeId);
        if (versions.isEmpty()) {
            throw new IllegalStateException("Resume has no versions");
        }
        ResumeVersion version = versions.get(0);
        updateStatus(resume, "PROCESSING", "PROCESSING", null);
        try {
            String text = version.getParsedText();
            if (text == null || text.isBlank()) {
                byte[] bytes = storageService.downloadFile(version.getFileUrl());
                Map<String, Object> extracted = gatewayClient.extractText(resume.getOriginalFilename(), resume.getMimeType(), bytes);
                text = String.valueOf(extracted.getOrDefault("text", ""));
                if (!Boolean.TRUE.equals(extracted.get("textLayerFound"))) {
                    updateStatus(resume, "NO_TEXT_LAYER", "FAILED",
                            "No selectable text was found in this file (it may be a scanned image). Upload a text-based PDF or DOCX.");
                    return resume;
                }
                if (text.length() > MAX_TEXT_CHARS) {
                    updateStatus(resume, "TEXT_EXTRACTED", "FAILED",
                            "Resume text is longer than " + MAX_TEXT_CHARS + " characters; please upload a shorter resume.");
                    return resume;
                }
                version.setParsedText(text);
                versionRepository.save(version);
            }

            AiDocument doc = documentRepository.findById(version.getId()).orElseGet(() -> AiDocument.builder()
                    .id(version.getId())
                    .createdAt(Instant.now())
                    .build());
            doc.setOwnerId(userId);
            doc.setDocumentType("RESUME");
            doc.setTitle(resume.getTitle() + " (v" + version.getVersionNumber() + ")");
            doc.setSource("resume:" + resumeId + ":v" + version.getVersionNumber());
            doc.setMimeType(resume.getMimeType());
            doc.setContent(text);
            doc.setVersion(version.getVersionNumber());
            doc.setStatus("CREATED");
            doc.setStructuredMetadata(null);
            doc.setUpdatedAt(Instant.now());
            documentRepository.save(doc);

            resumeIntelligenceService.processResume(doc.getId());
            updateStatus(resume, "PARSED", "READY", null);
        } catch (AiServiceException e) {
            updateStatus(resume, resume.getParsingStatus(), "FAILED", "AI processing failed (" + e.getCode() + "): " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            updateStatus(resume, resume.getParsingStatus(), "FAILED", "Processing failed: " + e.getMessage());
            throw e;
        }
        return resume;
    }

    private void updateStatus(Resume resume, String parsingStatus, String aiStatus, String error) {
        resume.setParsingStatus(parsingStatus);
        resume.setAiProcessingStatus(aiStatus);
        resume.setAiProcessingError(error != null && error.length() > 1000 ? error.substring(0, 1000) : error);
        resumeRepository.save(resume);
    }
}
