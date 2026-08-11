package com.careerpilot.backend.modules.resume.adapters.in.web;

import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.resume.services.ResumeService;
import com.careerpilot.shared.dto.resume.ResumeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import com.careerpilot.backend.modules.storage.StorageStatus;

@RestController
@RequestMapping("/api/v1/resumes")
@Tag(name = "Resume Management", description = "Endpoints for uploading, versioning, soft-deleting, and downloading resumes")
@SecurityRequirement(name = "bearerAuth")
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;

    public ResumeController(ResumeService resumeService, UserRepository userRepository) {
        this.resumeService = resumeService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "List resumes", description = "Retrieves active user resumes list")
    public ResponseEntity<List<ResumeDto>> listResumes(Principal principal) {
        UUID userId = getUserId(principal);
        return ResponseEntity.ok(resumeService.getActiveResumes(userId));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload resume", description = "Accepts a PDF/DOC/DOCX/TXT file under 10MB, saving it to MinIO")
    public ResponseEntity<ResumeDto> uploadResume(
            Principal principal,
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file) throws IOException {
        UUID userId = getUserId(principal);
        ResumeDto result = resumeService.uploadResume(
                userId,
                title,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download resume file")
    public ResponseEntity<Resource> downloadResume(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        byte[] fileBytes = resumeService.downloadResume(userId, id);
        ByteArrayResource resource = new ByteArrayResource(fileBytes);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resume_" + id + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete resume")
    public ResponseEntity<Void> deleteResume(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        resumeService.softDeleteResume(userId, id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Set default resume")
    public ResponseEntity<Void> setDefaultResume(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        resumeService.setDefaultResume(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/storage-status")
    @Operation(summary = "Get storage health status")
    public ResponseEntity<Map<String, String>> getStorageStatus() {
        StorageStatus health = resumeService.getStorageHealth();
        Map<String, String> response = new HashMap<>();
        response.put("status", health.name());
        return ResponseEntity.ok(response);
    }

    private UUID getUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
