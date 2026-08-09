package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ResumeSelectionService {

    private final ResumeRepository resumeRepository;
    private final DiscoveryJobRepository jobRepository;

    public ResumeSelectionService(ResumeRepository resumeRepository, DiscoveryJobRepository jobRepository) {
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
    }

    public SelectedResumeResult selectBestResume(UUID candidateId, UUID jobId) {
        List<Resume> activeResumes = resumeRepository.findActiveByUserId(candidateId);
        if (activeResumes.isEmpty()) {
            return SelectedResumeResult.builder()
                    .resumeId(null)
                    .title("No Active Resume Found")
                    .reason("Candidate has not uploaded an active resume. Application decision requires resume upload.")
                    .isDefault(false)
                    .build();
        }

        // 1. Check for default resume
        Optional<Resume> defaultOpt = activeResumes.stream().filter(Resume::isDefault).findFirst();
        Resume chosen = defaultOpt.orElse(activeResumes.get(0));

        String reason = defaultOpt.isPresent()
                ? "Primary default candidate resume selected."
                : "Active candidate resume selected (upload timestamp: " + chosen.getUploadedAt() + ").";

        return SelectedResumeResult.builder()
                .resumeId(chosen.getId())
                .title(chosen.getTitle() != null ? chosen.getTitle() : chosen.getOriginalFilename())
                .reason(reason)
                .isDefault(chosen.isDefault())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectedResumeResult {
        private UUID resumeId;
        private String title;
        private String reason;
        private boolean isDefault;
    }
}
