package com.careerpilot.backend.modules.resume.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.backend.modules.storage.StorageService;
import com.careerpilot.backend.modules.storage.StorageStatus;
import com.careerpilot.shared.dto.resume.ResumeDto;
import com.careerpilot.shared.dto.resume.ResumeVersionDto;
import com.careerpilot.shared.events.ResumeDeletedEvent;
import com.careerpilot.shared.events.ResumeMarkedDefaultEvent;
import com.careerpilot.shared.events.ResumeUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ResumeService {

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository resumeVersionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeService(
            ResumeRepository resumeRepository,
            ResumeVersionRepository resumeVersionRepository,
            UserRepository userRepository,
            StorageService storageService,
            ApplicationEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.resumeVersionRepository = resumeVersionRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
    }

    public List<ResumeDto> getActiveResumes(UUID userId) {
        return resumeRepository.findActiveByUserId(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResumeDto uploadResume(UUID userId, String title, String originalFilename, String mimeType, byte[] fileBytes) {
        StorageStatus health = storageService.checkHealth();
        if (health != StorageStatus.HEALTHY) {
            throw new IllegalStateException("Resume storage is temporarily unavailable: " + health.name());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validations
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Unsupported file type. Only PDF, DOC, DOCX, and TXT are supported.");
        }
        if (fileBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10MB limit.");
        }

        long activeCount = resumeRepository.countActiveResumesByUserId(userId);
        Optional<Resume> existingResumeOpt = resumeRepository.findByUserIdAndTitleAndNotDeleted(userId, title);

        // Check active limit
        if (existingResumeOpt.isEmpty() && activeCount >= 5) {
            throw new IllegalArgumentException("User has reached the limit of 5 active resumes. Please archive or delete an existing resume first.");
        }

        String checksum = calculateChecksum(fileBytes);
        
        Resume resume;
        int nextVersionNumber = 1;

        if (existingResumeOpt.isPresent()) {
            resume = existingResumeOpt.get();
            // Get next version number
            List<ResumeVersion> versions = resumeVersionRepository.findByResumeIdOrderByVersionNumberDesc(resume.getId());
            if (!versions.isEmpty()) {
                nextVersionNumber = versions.get(0).getVersionNumber() + 1;
            }
        } else {
            resume = Resume.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .title(title)
                    .fileUrl("")
                    .originalFilename(originalFilename)
                    .mimeType(mimeType)
                    .fileSize((long) fileBytes.length)
                    .checksumSha256(checksum)
                    .storageKey("")
                    .uploadedBy(userId)
                    .build();
        }

        String storageKey = "resumes/" + userId + "/" + resume.getId() + "_v" + nextVersionNumber + "_" + originalFilename;
        String fileUrl = storageService.uploadFile(storageKey, mimeType, fileBytes.length, new ByteArrayInputStream(fileBytes));

        resume.setFileUrl(fileUrl);
        resume.setStorageKey(storageKey);
        resume.setFileSize((long) fileBytes.length);
        resume.setChecksumSha256(checksum);

        Resume savedResume = resumeRepository.save(resume);

        ResumeVersion version = ResumeVersion.builder()
                .id(UUID.randomUUID())
                .resume(savedResume)
                .versionNumber(nextVersionNumber)
                .fileUrl(fileUrl)
                .createdBy(userId)
                .changeReason(nextVersionNumber == 1 ? "Initial upload" : "Updated version upload")
                .generatedByAi(false)
                .build();
        resumeVersionRepository.save(version);

        // Trigger Event
        ResumeUploadedEvent event = ResumeUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .resumeId(savedResume.getId())
                .fileName(originalFilename)
                .fileUrl(fileUrl)
                .contentType(mimeType)
                .fileSize(fileBytes.length)
                .build();
        eventPublisher.publishEvent(event);

        return mapToDto(savedResume);
    }

    public StorageStatus getStorageHealth() {
        return storageService.checkHealth();
    }

    @Transactional
    public void softDeleteResume(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findActiveById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Active resume not found"));

        if (!resume.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to resume");
        }

        resume.setDeleted(true);
        resume.setDeletedAt(Instant.now());
        resume.setDeletedBy(userId);

        if (resume.isDefault()) {
            resume.setDefault(false);
        }

        resumeRepository.save(resume);

        // Trigger Event
        ResumeDeletedEvent event = ResumeDeletedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .resumeId(resumeId)
                .deletedBy(userId)
                .build();
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void setDefaultResume(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findActiveById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Active resume not found"));

        if (!resume.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to resume");
        }

        // Clear existing default
        Optional<Resume> currentDefaultOpt = resumeRepository.findDefaultByUserId(userId);
        if (currentDefaultOpt.isPresent()) {
            Resume currentDefault = currentDefaultOpt.get();
            if (!currentDefault.getId().equals(resumeId)) {
                currentDefault.setDefault(false);
                resumeRepository.save(currentDefault);
            }
        }

        resume.setDefault(true);
        resumeRepository.save(resume);

        // Trigger Event
        ResumeMarkedDefaultEvent event = ResumeMarkedDefaultEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .resumeId(resumeId)
                .build();
        eventPublisher.publishEvent(event);
    }

    public byte[] downloadResume(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findActiveById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Active resume not found"));

        if (!resume.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to resume");
        }

        return storageService.downloadFile(resume.getFileUrl());
    }

    private String calculateChecksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }

    private ResumeDto mapToDto(Resume resume) {
        List<ResumeVersionDto> versionDtos = resumeVersionRepository.findByResumeIdOrderByVersionNumberDesc(resume.getId())
                .stream()
                .map(v -> ResumeVersionDto.builder()
                        .id(v.getId())
                        .versionNumber(v.getVersionNumber())
                        .fileUrl(v.getFileUrl())
                        .changeReason(v.getChangeReason())
                        .generatedByAi(v.isGeneratedByAi())
                        .createdAt(v.getCreatedAt())
                        .createdBy(v.getCreatedBy())
                        .build())
                .collect(Collectors.toList());

        return ResumeDto.builder()
                .id(resume.getId())
                .title(resume.getTitle())
                .fileUrl(resume.getFileUrl())
                .originalFilename(resume.getOriginalFilename())
                .mimeType(resume.getMimeType())
                .fileSize(resume.getFileSize())
                .checksumSha256(resume.getChecksumSha256())
                .storageKey(resume.getStorageKey())
                .parsingStatus(resume.getParsingStatus())
                .aiProcessingStatus(resume.getAiProcessingStatus())
                .processingError(resume.getAiProcessingError())
                .isDefault(resume.isDefault())
                .isArchived(resume.isArchived())
                .uploadedAt(resume.getUploadedAt())
                .uploadedBy(resume.getUploadedBy())
                .versions(versionDtos)
                .build();
    }
}
