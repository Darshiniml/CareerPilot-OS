package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.repositories.*;
import com.careerpilot.shared.events.CareerAutomationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class AgentExecutionService {

    private final AgentWorkflowRepository workflowRepository;
    private final AgentTaskRepository taskRepository;
    private final AgentPolicyRepository policyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentExecutionService(AgentWorkflowRepository workflowRepository,
                                 AgentTaskRepository taskRepository,
                                 AgentPolicyRepository policyRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
        this.policyRepository = policyRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentWorkflow startWorkflow(UUID userId) {
        // Create workflow
        UUID workflowId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        
        AgentWorkflow workflow = AgentWorkflow.builder()
                .id(workflowId)
                .userId(userId)
                .status(AgentWorkflowStatus.RUNNING)
                .correlationId(correlationId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        workflow = workflowRepository.save(workflow);

        // Define workflow tasks in order of execution priority
        String[] taskTypes = {
                "DISCOVER_NEW_JOBS",
                "FILTER_PERSONALIZED_JOBS",
                "MATCH_CANDIDATE",
                "CALCULATE_HISTORICAL_SIGNAL",
                "PRIORITIZE_OPPORTUNITIES",
                "RESEARCH_COMPANIES",
                "PREPARE_APPLICATION",
                "CHECK_PRE_FLIGHT",
                "WAIT_FOR_APPROVAL_OR_MANUAL_ACTION",
                "TRACK_APPLICATION",
                "UPDATE_ANALYTICS"
        };

        int priority = 100;
        for (String taskType : taskTypes) {
            AgentTask task = AgentTask.builder()
                    .id(UUID.randomUUID())
                    .workflowId(workflowId)
                    .taskType(taskType)
                    .status(AgentTaskStatus.PENDING)
                    .priority(priority)
                    .retryCount(0)
                    .maxRetries(3)
                    .payloadJson("{}")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            taskRepository.save(task);
            priority -= 10;
        }

        // Publish Start Event
        eventPublisher.publishEvent(CareerAutomationStartedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .correlationId(UUID.fromString(correlationId))
                .workflowId(workflowId)
                .userId(userId)
                .build());

        return workflow;
    }

    @Transactional
    public void pauseWorkflow(UUID workflowId) {
        AgentWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        workflow.setStatus(AgentWorkflowStatus.PAUSED);
        workflowRepository.save(workflow);
    }

    @Transactional
    public void resumeWorkflow(UUID workflowId) {
        AgentWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        workflow.setStatus(AgentWorkflowStatus.RUNNING);
        workflowRepository.save(workflow);
    }

    @Transactional
    public void cancelWorkflow(UUID workflowId) {
        AgentWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found"));
        workflow.setStatus(AgentWorkflowStatus.CANCELLED);
        workflowRepository.save(workflow);

        // Cancel pending tasks
        List<AgentTask> tasks = taskRepository.findByWorkflowId(workflowId);
        for (AgentTask task : tasks) {
            if (task.getStatus() == AgentTaskStatus.PENDING || task.getStatus() == AgentTaskStatus.RUNNING) {
                task.setStatus(AgentTaskStatus.SKIPPED);
                taskRepository.save(task);
            }
        }
    }

    @Transactional(readOnly = true)
    public AgentPolicy getOrCreatePolicy(UUID userId) {
        return policyRepository.findByUserId(userId)
                .orElseGet(() -> {
                    AgentPolicy policy = AgentPolicy.builder()
                            .id(UUID.randomUUID())
                            .userId(userId)
                            .enabled(true)
                            .maxApplicationsPerDay(5)
                            .minimumMatchScore(70.0)
                            .allowedEmploymentTypes("FULL_TIME,PART_TIME,CONTRACT")
                            .allowedLocations("REMOTE,HYBRID,BANGALORE")
                            .allowedRemoteTypes("REMOTE,HYBRID,ON_SITE")
                            .requireApproval(true)
                            .allowAutomaticSubmission(false) // Safe default MUST be false
                            .allowReferenceResearch(true)
                            .allowExternalConnectors(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    return policyRepository.save(policy);
                });
    }

    @Transactional
    public AgentPolicy updatePolicy(UUID userId, AgentPolicy updated) {
        AgentPolicy existing = getOrCreatePolicy(userId);
        
        existing.setEnabled(updated.getEnabled() != null ? updated.getEnabled() : existing.getEnabled());
        existing.setMaxApplicationsPerDay(updated.getMaxApplicationsPerDay() != null ? updated.getMaxApplicationsPerDay() : existing.getMaxApplicationsPerDay());
        existing.setMinimumMatchScore(updated.getMinimumMatchScore() != null ? updated.getMinimumMatchScore() : existing.getMinimumMatchScore());
        existing.setAllowedEmploymentTypes(updated.getAllowedEmploymentTypes() != null ? updated.getAllowedEmploymentTypes() : existing.getAllowedEmploymentTypes());
        existing.setAllowedLocations(updated.getAllowedLocations() != null ? updated.getAllowedLocations() : existing.getAllowedLocations());
        existing.setAllowedRemoteTypes(updated.getAllowedRemoteTypes() != null ? updated.getAllowedRemoteTypes() : existing.getAllowedRemoteTypes());
        existing.setAllowedCompanies(updated.getAllowedCompanies());
        existing.setBlockedCompanies(updated.getBlockedCompanies());
        existing.setRequireApproval(updated.getRequireApproval() != null ? updated.getRequireApproval() : existing.getRequireApproval());
        existing.setAllowAutomaticSubmission(updated.getAllowAutomaticSubmission() != null ? updated.getAllowAutomaticSubmission() : existing.getAllowAutomaticSubmission());
        existing.setAllowReferenceResearch(updated.getAllowReferenceResearch() != null ? updated.getAllowReferenceResearch() : existing.getAllowReferenceResearch());
        existing.setAllowExternalConnectors(updated.getAllowExternalConnectors() != null ? updated.getAllowExternalConnectors() : existing.getAllowExternalConnectors());
        
        return policyRepository.save(existing);
    }
}
