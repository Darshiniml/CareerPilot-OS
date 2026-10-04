package com.careerpilot.backend.modules.application.adapters.in.web;

import com.careerpilot.backend.modules.application.domain.*;
import com.careerpilot.backend.modules.application.services.*;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Application orchestration endpoints")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationOrchestratorService orchestratorService;
    private final ApplicationSubmissionRegistry submissionRegistry;
    private final SubmissionPreflightService preflightService;
    private final ApplicationDecisionService decisionService;
    private final ApplicationPackageService packageService;
    private final ApplicationTrackingService trackingService;
    private final ApplicationTimelineService timelineService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    @Operation(summary = "Create a new application record")
    public ResponseEntity<ApplicationRecord> create(@Valid @RequestBody CreateApplicationRequest request, Principal principal) {
        UUID candidateId = getUserId(principal);
        ApplicationRecord created = orchestratorService.createApplication(candidateId, request.companyId(), request.jobId(), request.connectorId(), request.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve application for submission")
    public ResponseEntity<ApplicationRecord> approve(@PathVariable UUID id, @RequestBody ApprovalRequest request, Principal principal) {
        UUID userId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orchestratorService.approve(id, userId, request.ipAddress()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject application submission")
    public ResponseEntity<ApplicationRecord> reject(@PathVariable UUID id, @RequestBody ApprovalRequest request, Principal principal) {
        UUID userId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orchestratorService.reject(id, userId, request.reason(), request.ipAddress()));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Execute application submission or manual fallback")
    public ResponseEntity<ApplicationRecord> submit(@PathVariable UUID id, @RequestBody ApprovalRequest request, Principal principal) {
        UUID userId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orchestratorService.submit(id, userId, request.ipAddress()));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry submission for transient failures")
    public ResponseEntity<ApplicationRecord> retry(@PathVariable UUID id, @RequestBody ApprovalRequest request, Principal principal) {
        UUID userId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orchestratorService.retry(id, userId, request.ipAddress()));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Manual candidate status update (e.g. INTERVIEW, OFFER, REJECTED, WITHDRAWN)")
    public ResponseEntity<ApplicationRecord> updateStatus(@PathVariable UUID id, @RequestBody StatusUpdateRequest request, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        WorkflowState targetState;
        try {
            targetState = WorkflowState.valueOf(request.targetState());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        ApplicationRecord updated = trackingService.transitionState(id, targetState, actorId, "USER", request.reason());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Submit empirical verification evidence")
    public ResponseEntity<ApplicationVerificationResult> verify(@PathVariable UUID id, @RequestBody VerifyRequest request, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ApplicationVerificationResult result = trackingService.verifyApplicationWithEvidence(id, request.evidenceType(), request.evidenceReference(), request.confirmationId(), actorId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get unified application timeline (state history + communication-derived events)")
    public ResponseEntity<List<ApplicationTimelineService.TimelineEntry>> getTimeline(@PathVariable UUID id, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(timelineService.getTimeline(id));
    }

    @GetMapping("/{id}/preflight")
    @Operation(summary = "Evaluate application preflight checks")
    public ResponseEntity<SubmissionPreflightService.PreflightResult> preflight(@PathVariable UUID id, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SubmissionPreflightService.PreflightResult result = preflightService.evaluatePreflight(app, actorId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/decision")
    @Operation(summary = "Get intelligent application decision recommendation")
    public ResponseEntity<ApplicationDecision> getDecision(@PathVariable UUID id, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ApplicationDecision decision = decisionService.getDecision(id)
                .orElseGet(() -> decisionService.evaluateDecision(id));
        return ResponseEntity.ok(decision);
    }

    @PostMapping("/{id}/decision/evaluate")
    @Operation(summary = "Force recalculate application decision")
    public ResponseEntity<ApplicationDecision> evaluateDecision(@PathVariable UUID id, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(decisionService.evaluateDecision(id));
    }

    @GetMapping("/{id}/package")
    @Operation(summary = "Get comprehensive application package")
    public ResponseEntity<ApplicationPackage> getPackage(@PathVariable UUID id, Principal principal) {
        UUID actorId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(actorId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ApplicationPackage pkg = packageService.getPackage(id)
                .orElseGet(() -> packageService.assemblePackage(id));
        return ResponseEntity.ok(pkg);
    }

    @GetMapping("/submission-capabilities")
    @Operation(summary = "List submission capabilities across all connector sources")
    public ResponseEntity<List<ApplicationSubmissionCapability>> getSubmissionCapabilities() {
        return ResponseEntity.ok(submissionRegistry.getSubmissionCapabilities());
    }

    @GetMapping
    @Operation(summary = "List candidate application records")
    public ResponseEntity<List<ApplicationRecord>> list(Principal principal) {
        UUID userId = getUserId(principal);
        List<ApplicationRecord> list = orchestratorService.listApplications().stream()
                .filter(a -> a.getCandidateId().equals(userId))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application details by ID")
    public ResponseEntity<ApplicationRecord> get(@PathVariable UUID id, Principal principal) {
        UUID userId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(id);
        if (!app.getCandidateId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(app);
    }

    @GetMapping("/history")
    @Operation(summary = "Get application state history")
    public ResponseEntity<List<ApplicationHistory>> history(@RequestParam UUID applicationId, Principal principal) {
        UUID userId = getUserId(principal);
        ApplicationRecord app = orchestratorService.getApplication(applicationId);
        if (!app.getCandidateId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orchestratorService.history(applicationId));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get application statistics")
    public ResponseEntity<Map<String, Object>> statistics(Principal principal) {
        // Scoped to candidate applications
        UUID userId = getUserId(principal);
        Map<String, Object> stats = orchestratorService.statistics(userId);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/policy")
    @Operation(summary = "Save candidate application approval policy")
    public ResponseEntity<ApprovalPolicy> savePolicy(@RequestBody ApprovalPolicy policy, Principal principal) {
        UUID userId = getUserId(principal);
        policy.setCandidateId(userId);
        return ResponseEntity.ok(orchestratorService.savePolicy(policy));
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) throw new SecurityException("Unauthorized");
        return userRepository.findByEmail(principal.getName())
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    public record CreateApplicationRequest(UUID candidateId, UUID companyId, UUID jobId, String connectorId, Map<String, Object> metadata) {}

    public record ApprovalRequest(UUID actorId, String reason, String ipAddress) {}

    public record StatusUpdateRequest(String targetState, String reason) {}

    public record VerifyRequest(String evidenceType, String evidenceReference, String confirmationId) {}
}
