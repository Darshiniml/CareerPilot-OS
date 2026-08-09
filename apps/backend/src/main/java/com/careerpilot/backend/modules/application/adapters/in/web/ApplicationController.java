package com.careerpilot.backend.modules.application.adapters.in.web;

import com.careerpilot.backend.modules.application.domain.*;
import com.careerpilot.backend.modules.application.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/create")
    @Operation(summary = "Create a new application record")
    public ResponseEntity<ApplicationRecord> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationRecord created = orchestratorService.createApplication(request.candidateId(), request.companyId(), request.jobId(), request.connectorId(), request.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve application for submission")
    public ResponseEntity<ApplicationRecord> approve(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.approve(id, request.actorId(), request.ipAddress()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject application submission")
    public ResponseEntity<ApplicationRecord> reject(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.reject(id, request.actorId(), request.reason(), request.ipAddress()));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Execute application submission or manual fallback")
    public ResponseEntity<ApplicationRecord> submit(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.submit(id, request.actorId(), request.ipAddress()));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry submission for transient failures")
    public ResponseEntity<ApplicationRecord> retry(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.retry(id, request.actorId(), request.ipAddress()));
    }

    @GetMapping("/{id}/preflight")
    @Operation(summary = "Evaluate application preflight checks")
    public ResponseEntity<SubmissionPreflightService.PreflightResult> preflight(@PathVariable UUID id) {
        ApplicationRecord record = orchestratorService.getApplication(id);
        SubmissionPreflightService.PreflightResult result = preflightService.evaluatePreflight(record, record.getCandidateId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/decision")
    @Operation(summary = "Get intelligent application decision recommendation")
    public ResponseEntity<ApplicationDecision> getDecision(@PathVariable UUID id) {
        ApplicationDecision decision = decisionService.getDecision(id)
                .orElseGet(() -> decisionService.evaluateDecision(id));
        return ResponseEntity.ok(decision);
    }

    @PostMapping("/{id}/decision/evaluate")
    @Operation(summary = "Force recalculate application decision")
    public ResponseEntity<ApplicationDecision> evaluateDecision(@PathVariable UUID id) {
        return ResponseEntity.ok(decisionService.evaluateDecision(id));
    }

    @GetMapping("/{id}/package")
    @Operation(summary = "Get comprehensive application package")
    public ResponseEntity<ApplicationPackage> getPackage(@PathVariable UUID id) {
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
    public ResponseEntity<List<ApplicationRecord>> list() {
        return ResponseEntity.ok(orchestratorService.listApplications());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application details by ID")
    public ResponseEntity<ApplicationRecord> get(@PathVariable UUID id) {
        return ResponseEntity.ok(orchestratorService.getApplication(id));
    }

    @GetMapping("/history")
    @Operation(summary = "Get application state history")
    public ResponseEntity<List<ApplicationHistory>> history(@RequestParam UUID applicationId) {
        return ResponseEntity.ok(orchestratorService.history(applicationId));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get application statistics")
    public ResponseEntity<Map<String, Object>> statistics() {
        return ResponseEntity.ok(orchestratorService.statistics());
    }

    @PostMapping("/policy")
    @Operation(summary = "Save candidate application approval policy")
    public ResponseEntity<ApprovalPolicy> savePolicy(@RequestBody ApprovalPolicy policy) {
        return ResponseEntity.ok(orchestratorService.savePolicy(policy));
    }

    public record CreateApplicationRequest(UUID candidateId, UUID companyId, UUID jobId, String connectorId, Map<String, Object> metadata) {
    }

    public record ApprovalRequest(UUID actorId, String reason, String ipAddress) {
    }
}
