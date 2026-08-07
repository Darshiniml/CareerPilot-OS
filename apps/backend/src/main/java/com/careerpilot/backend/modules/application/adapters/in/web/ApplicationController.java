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

    @PostMapping("/create")
    @Operation(summary = "Create a new application")
    public ResponseEntity<ApplicationRecord> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationRecord created = orchestratorService.createApplication(request.candidateId(), request.companyId(), request.jobId(), request.connectorId(), request.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApplicationRecord> approve(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.approve(id, request.actorId(), request.ipAddress()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApplicationRecord> reject(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.reject(id, request.actorId(), request.reason(), request.ipAddress()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApplicationRecord> submit(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.submit(id, request.actorId(), request.ipAddress()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApplicationRecord> retry(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(orchestratorService.retry(id, request.actorId(), request.ipAddress()));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationRecord>> list() {
        return ResponseEntity.ok(orchestratorService.listApplications());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationRecord> get(@PathVariable UUID id) {
        return ResponseEntity.ok(orchestratorService.getApplication(id));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ApplicationHistory>> history(@RequestParam UUID applicationId) {
        return ResponseEntity.ok(orchestratorService.history(applicationId));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics() {
        return ResponseEntity.ok(orchestratorService.statistics());
    }

    @PostMapping("/policy")
    public ResponseEntity<ApprovalPolicy> savePolicy(@RequestBody ApprovalPolicy policy) {
        return ResponseEntity.ok(orchestratorService.savePolicy(policy));
    }

    public record CreateApplicationRequest(UUID candidateId, UUID companyId, UUID jobId, String connectorId, Map<String, Object> metadata) {
    }

    public record ApprovalRequest(UUID actorId, String reason, String ipAddress) {
    }
}
