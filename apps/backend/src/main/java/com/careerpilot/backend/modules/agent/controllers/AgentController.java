package com.careerpilot.backend.modules.agent.controllers;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.repositories.*;
import com.careerpilot.backend.modules.agent.services.AgentExecutionService;
import com.careerpilot.backend.modules.agent.services.AgentOrchestrator;
import com.careerpilot.backend.modules.agent.services.AgentRegistry;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agents")
@Tag(name = "Autonomous Career Agent Orchestration", description = "Endpoints for workflow state machines, policy engines, and active execution logs")
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentExecutionService executionService;
    private final AgentOrchestrator orchestrator;
    private final AgentRegistry registry;
    private final UserRepository userRepository;
    private final AgentWorkflowRepository workflowRepository;
    private final AgentTaskRepository taskRepository;

    public AgentController(AgentExecutionService executionService,
                           AgentOrchestrator orchestrator,
                           AgentRegistry registry,
                           UserRepository userRepository,
                           AgentWorkflowRepository workflowRepository,
                           AgentTaskRepository taskRepository) {
        this.executionService = executionService;
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.userRepository = userRepository;
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
    }

    private UUID getUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }

    @PostMapping("/workflow/start")
    @Operation(summary = "Start autonomous career workflow run")
    public ResponseEntity<AgentWorkflow> startWorkflow(Principal principal) {
        UUID userId = getUserId(principal);
        AgentWorkflow workflow = executionService.startWorkflow(userId);
        return ResponseEntity.ok(workflow);
    }

    @PostMapping("/workflow/{id}/pause")
    @Operation(summary = "Pause career workflow run")
    public ResponseEntity<Void> pauseWorkflow(@PathVariable("id") UUID id, Principal principal) {
        // Enforce user scoping
        UUID userId = getUserId(principal);
        AgentWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        if (!workflow.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        executionService.pauseWorkflow(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/workflow/{id}/resume")
    @Operation(summary = "Resume career workflow run")
    public ResponseEntity<Void> resumeWorkflow(@PathVariable("id") UUID id, Principal principal) {
        UUID userId = getUserId(principal);
        AgentWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        if (!workflow.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        executionService.resumeWorkflow(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/workflow/{id}/cancel")
    @Operation(summary = "Cancel career workflow run")
    public ResponseEntity<Void> cancelWorkflow(@PathVariable("id") UUID id, Principal principal) {
        UUID userId = getUserId(principal);
        AgentWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        if (!workflow.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        executionService.cancelWorkflow(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/workflow/active")
    @Operation(summary = "Get active workflows of candidate")
    public ResponseEntity<List<AgentWorkflow>> getActiveWorkflows(Principal principal) {
        UUID userId = getUserId(principal);
        List<AgentWorkflow> list = workflowRepository.findByUserIdAndStatus(userId, AgentWorkflowStatus.RUNNING);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/workflow/{id}/status")
    @Operation(summary = "Get tasks timeline for workflow")
    public ResponseEntity<Map<String, Object>> getWorkflowTasks(@PathVariable("id") UUID id, Principal principal) {
        UUID userId = getUserId(principal);
        AgentWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        if (!workflow.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        List<AgentTask> tasks = taskRepository.findByWorkflowIdOrderByPriorityDescCreatedAtAsc(id);
        
        long totalTasks = tasks.size();
        long completedTasks = tasks.stream()
                .filter(t -> t.getStatus() == AgentTaskStatus.COMPLETED || t.getStatus() == AgentTaskStatus.SKIPPED)
                .count();
        
        int progress = totalTasks > 0 ? (int) (completedTasks * 100 / totalTasks) : 0;
        
        String currentTask = tasks.stream()
                .filter(t -> t.getStatus() == AgentTaskStatus.RUNNING || t.getStatus() == AgentTaskStatus.RETRYING)
                .map(AgentTask::getTaskType)
                .findFirst()
                .orElse(null);
                
        Map<String, Object> response = new HashMap<>();
        response.put("workflowId", workflow.getId());
        response.put("status", workflow.getStatus().name());
        response.put("progress", progress);
        response.put("completedTasks", completedTasks);
        response.put("totalTasks", totalTasks);
        response.put("currentTask", currentTask);
        response.put("tasks", tasks);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Get currently active agent counts and details")
    public ResponseEntity<Map<String, Object>> getActiveAgents(Principal principal) {
        // Exposes exact active execution tracking
        return ResponseEntity.ok(orchestrator.getActiveExecutions());
    }

    @GetMapping("/policy")
    @Operation(summary = "Read candidate career automation policy rules")
    public ResponseEntity<AgentPolicy> getPolicy(Principal principal) {
        UUID userId = getUserId(principal);
        AgentPolicy policy = executionService.getOrCreatePolicy(userId);
        return ResponseEntity.ok(policy);
    }

    @PutMapping("/policy")
    @Operation(summary = "Update candidate career automation policy rules")
    public ResponseEntity<AgentPolicy> updatePolicy(@RequestBody AgentPolicy updatedPolicy, Principal principal) {
        UUID userId = getUserId(principal);
        AgentPolicy saved = executionService.updatePolicy(userId, updatedPolicy);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/registry")
    @Operation(summary = "Query agent registry health and details")
    public ResponseEntity<List<Map<String, Object>>> getRegistry() {
        List<Map<String, Object>> result = registry.list().stream().map(agent -> {
            Map<String, Object> details = new HashMap<>();
            details.put("agentId", agent.getAgentId());
            details.put("name", agent.getName());
            details.put("version", agent.getVersion());
            details.put("capabilities", agent.getCapabilities());
            details.put("supportedTaskTypes", agent.getSupportedTaskTypes());
            details.put("health", agent.getHealthStatus().name());
            details.put("enabled", registry.isEnabled(agent.getAgentId()));
            return details;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
