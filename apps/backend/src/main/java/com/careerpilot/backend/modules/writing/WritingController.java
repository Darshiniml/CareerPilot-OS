package com.careerpilot.backend.modules.writing;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService.ResumeKnowledge;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.ai.matching.MatchService;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

/**
 * AI writing grounded in the candidate's real data: cover letters (saved, editable, regenerable)
 * and application preparation. Generated text is fact-checked against the supplied data.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "AI Writing", description = "Cover letters and application preparation from your real data")
@RequiredArgsConstructor
public class WritingController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CoverLetterRepository coverLetterRepository;
    private final CandidateKnowledgeService candidateKnowledgeService;
    private final JobContextService jobContextService;
    private final ApplicationRecordRepository applicationRepository;
    private final MatchService matchService;
    private final AiGatewayClient gatewayClient;
    private final CurrentUser currentUser;

    public record CoverLetterRequest(UUID jobId, UUID applicationId, String tone, String notes, String hiringManagerName) {
    }

    @PostMapping("/cover-letters")
    @Operation(summary = "Generate a tailored cover letter (saved as a new letter)")
    public ResponseEntity<CoverLetter> generate(@RequestBody CoverLetterRequest request, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        UUID jobId = request.jobId();
        if (request.applicationId() != null) {
            ApplicationRecord app = applicationRepository.findById(request.applicationId())
                    .filter(a -> userId.equals(a.getCandidateId()))
                    .orElseThrow(() -> new NoSuchElementException("Application not found"));
            jobId = app.getJobId();
        }
        if (jobId == null) {
            throw new IllegalArgumentException("jobId or applicationId is required");
        }
        ResumeKnowledge resume = candidateKnowledgeService.primaryResume(userId).orElseThrow(() ->
                new IllegalStateException("Upload and process a resume first: the letter may only use facts from it"));
        DiscoveryJob job = jobContextService.requireJob(jobId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("candidate", candidateKnowledgeService.candidateSummary(userId));
        payload.put("job", jobContextService.jobCard(job));
        payload.put("jobDescription", jobContextService.jobText(job));
        payload.put("resumeText", resume.text());
        if (request.tone() != null) payload.put("tone", request.tone());
        if (request.notes() != null) payload.put("notes", request.notes());
        if (request.hiringManagerName() != null && !request.hiringManagerName().isBlank()) {
            payload.put("hiringManagerName", request.hiringManagerName().trim());
        }
        Map<String, Object> result = gatewayClient.run("COVER_LETTER", payload);
        String letter = Objects.toString(result.get("letter"), "");
        if (letter.isBlank()) {
            throw new IllegalStateException("The AI returned an empty letter; please try again");
        }
        Instant now = Instant.now();
        CoverLetter saved = coverLetterRepository.save(CoverLetter.builder()
                .id(UUID.randomUUID()).userId(userId).jobId(jobId).applicationId(request.applicationId())
                .resumeId(resume.resumeId()).resumeVersion(resume.versionNumber())
                .tone(request.tone()).content(letter).verificationJson(json(result.get("verification")))
                .createdAt(now).updatedAt(now).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/cover-letters")
    public ResponseEntity<List<CoverLetter>> list(Principal principal) {
        return ResponseEntity.ok(coverLetterRepository.findByUserIdOrderByCreatedAtDesc(currentUser.requireId(principal)));
    }

    @PutMapping("/cover-letters/{id}")
    public ResponseEntity<CoverLetter> edit(@PathVariable UUID id, @RequestBody Map<String, String> body, Principal principal) {
        CoverLetter letter = coverLetterRepository.findByIdAndUserId(id, currentUser.requireId(principal))
                .orElseThrow(() -> new NoSuchElementException("Cover letter not found"));
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        letter.setContent(content);
        letter.setEditedByUser(true);
        letter.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(coverLetterRepository.save(letter));
    }

    @DeleteMapping("/cover-letters/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        CoverLetter letter = coverLetterRepository.findByIdAndUserId(id, currentUser.requireId(principal))
                .orElseThrow(() -> new NoSuchElementException("Cover letter not found"));
        coverLetterRepository.delete(letter);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/applications/{applicationId}/preparation")
    @Operation(summary = "AI application preparation: checklist, talking points, tailoring tips, honest risks")
    public ResponseEntity<Map<String, Object>> preparation(@PathVariable UUID applicationId, Principal principal) {
        UUID userId = currentUser.requireId(principal);
        ApplicationRecord app = applicationRepository.findById(applicationId)
                .filter(a -> userId.equals(a.getCandidateId()))
                .orElseThrow(() -> new NoSuchElementException("Application not found"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("job", jobContextService.jobContext(app.getJobId()));
        payload.put("candidate", candidateKnowledgeService.candidateSummary(userId));
        matchService.matchIfPossible(userId, app.getJobId()).ifPresent(m -> payload.put("match", Map.of(
                "overallScore", m.getOverallScore(),
                "matchedSkills", m.getMatchedSkills() == null ? List.of() : m.getMatchedSkills(),
                "missingSkills", m.getMissingSkills() == null ? List.of() : m.getMissingSkills())));
        return ResponseEntity.ok(gatewayClient.run("APPLICATION_PREPARATION", payload));
    }

    private static String json(Object o) {
        try {
            return o == null ? null : MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
