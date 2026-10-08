package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class AgentOrchestrator {

    private final AgentWorkflowRepository workflowRepository;
    private final AgentTaskRepository taskRepository;
    private final AgentExecutionRepository executionRepository;
    private final AgentExecutionService executionService;
    private final AgentRegistry agentRegistry;
    private final ExecutorService executorService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String toJson(Map<String, Object> map) {
        if (map == null) return "{}";
        try { return objectMapper.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }

    public AgentOrchestrator(AgentWorkflowRepository workflowRepository,
                             AgentTaskRepository taskRepository,
                             AgentExecutionRepository executionRepository,
                             AgentExecutionService executionService,
                             AgentRegistry agentRegistry,
                             MeterRegistry meterRegistry) {
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.agentRegistry = agentRegistry;
        this.meterRegistry = meterRegistry;
        this.executorService = Executors.newFixedThreadPool(4); // Bound concurrent execution
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Agent Orchestrator started");
        log.info("Scheduler enabled");
        log.info("Registered agents: {}", agentRegistry.list().size());
        log.info("Pending-task polling enabled");
    }

    @Scheduled(fixedDelay = 3000)
    public void pollAndExecute() {
        // Poll for pending workflows
        List<AgentTask> pendingTasks = taskRepository.findByStatus(AgentTaskStatus.PENDING);
        List<AgentTask> retryingTasks = taskRepository.findByStatus(AgentTaskStatus.RETRYING);
        
        List<AgentTask> tasksToRun = new ArrayList<>();
        tasksToRun.addAll(pendingTasks);
        tasksToRun.addAll(retryingTasks);
        
        if (!tasksToRun.isEmpty()) {
            log.info("[AgentOrchestrator] Found {} pending task(s)", tasksToRun.size());
        } else {
            log.debug("[AgentOrchestrator] Found 0 pending task(s)");
        }
        
        // Sort by priority desc
        tasksToRun.sort(Comparator.comparingInt(AgentTask::getPriority).reversed());
        
        for (AgentTask task : tasksToRun) {
            Optional<AgentWorkflow> workflowOpt = workflowRepository.findById(task.getWorkflowId());
            if (workflowOpt.isPresent() && workflowOpt.get().getStatus() == AgentWorkflowStatus.RUNNING) {
                // Ensure task prerequisites are met: previous tasks must be COMPLETED
                if (isPrerequisiteMet(task)) {
                    log.info("[AgentOrchestrator] Dispatching task={} type={}", task.getId(), task.getTaskType());
                    claimAndDispatch(task, workflowOpt.get());
                }
            }
        }
    }

    private boolean isPrerequisiteMet(AgentTask task) {
        List<AgentTask> workflowTasks = taskRepository.findByWorkflowIdOrderByPriorityDescCreatedAtAsc(task.getWorkflowId());
        for (AgentTask t : workflowTasks) {
            if (t.getPriority() > task.getPriority()) {
                if (t.getStatus() != AgentTaskStatus.COMPLETED && t.getStatus() != AgentTaskStatus.SKIPPED) {
                    return false;
                }
            }
        }
        return true;
    }

    private synchronized void claimAndDispatch(AgentTask task, AgentWorkflow workflow) {
        // Claim the task
        task.setStatus(AgentTaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        taskRepository.save(task);
        
        log.info("Claimed task {} ({}) for workflow {}", task.getId(), task.getTaskType(), workflow.getId());
        
        executorService.submit(() -> executeTask(task, workflow));
    }

    private void executeTask(AgentTask task, AgentWorkflow workflow) {
        // Register metrics
        if (meterRegistry != null) {
            meterRegistry.counter("agent_tasks_total", "type", task.getTaskType()).increment();
        }

        // 1. Create execution context
        AgentPolicy policy = executionService.getOrCreatePolicy(workflow.getUserId());
        AgentContext context = AgentContext.builder()
                .userId(workflow.getUserId())
                .workflowId(workflow.getId())
                .correlationId(workflow.getCorrelationId())
                .policy(policy)
                .globalData(new ConcurrentHashMap<>())
                .build();

        // 2. Resolve Agent
        Optional<CareerAgent> agentOpt = agentRegistry.findByTaskType(task.getTaskType());
        if (agentOpt.isEmpty()) {
            failTask(task, "No active agent found for task type: " + task.getTaskType());
            return;
        }

        CareerAgent agent = agentOpt.get();
        task.setAgentId(agent.getAgentId());
        taskRepository.save(task);

        // 3. Create Execution record
        AgentExecution execution = AgentExecution.builder()
                .id(UUID.randomUUID())
                .taskId(task.getId())
                .agentId(agent.getAgentId())
                .status(AgentExecutionStatus.RUNNING)
                .payloadJson(task.getPayloadJson())
                .startedAt(Instant.now())
                .build();
        execution = executionRepository.save(execution);

        try {
            // 4. Run Execution
            AgentResult result = agent.execute(context, task);
            
            // 5. Update state based on outcome
            if (result.getStatus() == AgentResult.Status.SUCCESS) {
                completeTask(task, execution, result);
            } else if (result.getStatus() == AgentResult.Status.BLOCKED) {
                blockWorkflow(task, execution, result);
            } else if (result.getStatus() == AgentResult.Status.UNAVAILABLE) {
                // Keep workflow running even if reference research is unavailable
                completeTaskAsUnavailable(task, execution, result);
            } else if (result.getStatus() == AgentResult.Status.UNSUPPORTED_CONNECTOR) {
                // Soft fail or mark skipped
                task.setStatus(AgentTaskStatus.SKIPPED);
                task.setCompletedAt(Instant.now());
                task.setErrorMessage(result.getMessage());
                taskRepository.save(task);
                
                execution.setStatus(AgentExecutionStatus.COMPLETED);
                execution.setCompletedAt(Instant.now());
                execution.setResultJson(toJson(result.getOutputData()));
                executionRepository.save(execution);
            } else {
                handleTaskFailure(task, execution, result.getMessage(), result.getException());
            }
            
        } catch (Exception e) {
            handleTaskFailure(task, execution, e.getMessage(), e);
        }
    }

    private void blockWorkflow(AgentTask task, AgentExecution execution, AgentResult result) {
        task.setStatus(AgentTaskStatus.BLOCKED);
        task.setCompletedAt(Instant.now());
        task.setErrorMessage("BLOCKED: " + result.getMessage());
        taskRepository.save(task);

        execution.setStatus(AgentExecutionStatus.FAILED);
        execution.setCompletedAt(Instant.now());
        execution.setErrorMessage("BLOCKED: " + result.getMessage());
        executionRepository.save(execution);

        Optional<AgentWorkflow> workflowOpt = workflowRepository.findById(task.getWorkflowId());
        if (workflowOpt.isPresent()) {
            AgentWorkflow workflow = workflowOpt.get();
            workflow.setStatus(AgentWorkflowStatus.BLOCKED);
            workflowRepository.save(workflow);
        }
        log.warn("Task {} ({}) blocked workflow: {}", task.getId(), task.getTaskType(), result.getMessage());
    }

    private void completeTask(AgentTask task, AgentExecution execution, AgentResult result) {
        task.setStatus(AgentTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setResultJson(toJson(result.getOutputData()));
        taskRepository.save(task);

        execution.setStatus(AgentExecutionStatus.COMPLETED);
        execution.setCompletedAt(Instant.now());
        execution.setResultJson(toJson(result.getOutputData()));
        executionRepository.save(execution);

        log.info("Task {} ({}) completed successfully.", task.getId(), task.getTaskType());
        checkWorkflowCompletion(task.getWorkflowId());
    }

    private void completeTaskAsUnavailable(AgentTask task, AgentExecution execution, AgentResult result) {
        task.setStatus(AgentTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setErrorMessage("UNAVAILABLE: " + result.getMessage());
        task.setResultJson(toJson(result.getOutputData()));
        taskRepository.save(task);

        execution.setStatus(AgentExecutionStatus.COMPLETED);
        execution.setCompletedAt(Instant.now());
        execution.setErrorMessage("UNAVAILABLE: " + result.getMessage());
        execution.setResultJson(toJson(result.getOutputData()));
        executionRepository.save(execution);

        log.warn("Task {} ({}) completed with UNAVAILABLE status: {}", task.getId(), task.getTaskType(), result.getMessage());
        checkWorkflowCompletion(task.getWorkflowId());
    }

    private void handleTaskFailure(AgentTask task, AgentExecution execution, String error, Throwable ex) {
        log.error("Task {} ({}) failed: {}", task.getId(), task.getTaskType(), error, ex);
        
        execution.setStatus(AgentExecutionStatus.FAILED);
        execution.setCompletedAt(Instant.now());
        execution.setErrorMessage(error);
        executionRepository.save(execution);

        if (task.getRetryCount() < task.getMaxRetries()) {
            task.setRetryCount(task.getRetryCount() + 1);
            task.setStatus(AgentTaskStatus.RETRYING);
            task.setErrorMessage(error);
            taskRepository.save(task);
            log.info("Retrying task {} (attempt {})", task.getId(), task.getRetryCount());
        } else {
            task.setStatus(AgentTaskStatus.FAILED);
            task.setCompletedAt(Instant.now());
            task.setErrorMessage(error);
            taskRepository.save(task);
            
            // Mark entire workflow as failed
            Optional<AgentWorkflow> workflowOpt = workflowRepository.findById(task.getWorkflowId());
            if (workflowOpt.isPresent()) {
                AgentWorkflow workflow = workflowOpt.get();
                workflow.setStatus(AgentWorkflowStatus.FAILED);
                workflowRepository.save(workflow);
            }
        }
    }

    private void failTask(AgentTask task, String error) {
        task.setStatus(AgentTaskStatus.FAILED);
        task.setCompletedAt(Instant.now());
        task.setErrorMessage(error);
        taskRepository.save(task);

        Optional<AgentWorkflow> workflowOpt = workflowRepository.findById(task.getWorkflowId());
        if (workflowOpt.isPresent()) {
            AgentWorkflow workflow = workflowOpt.get();
            workflow.setStatus(AgentWorkflowStatus.FAILED);
            workflowRepository.save(workflow);
        }
    }

    private void checkWorkflowCompletion(UUID workflowId) {
        List<AgentTask> tasks = taskRepository.findByWorkflowId(workflowId);
        boolean allFinished = true;
        for (AgentTask task : tasks) {
            if (task.getStatus() != AgentTaskStatus.COMPLETED && task.getStatus() != AgentTaskStatus.SKIPPED) {
                allFinished = false;
                break;
            }
        }
        
        if (allFinished) {
            Optional<AgentWorkflow> workflowOpt = workflowRepository.findById(workflowId);
            if (workflowOpt.isPresent()) {
                AgentWorkflow workflow = workflowOpt.get();
                workflow.setStatus(AgentWorkflowStatus.COMPLETED);
                workflowRepository.save(workflow);
                log.info("Workflow {} completed successfully.", workflowId);
            }
        }
    }

    public Map<String, Object> getActiveExecutions(UUID userId) {
        List<AgentExecution> activeExecutions = executionRepository.findByStatus(AgentExecutionStatus.RUNNING);
        List<Map<String, Object>> exList = new ArrayList<>();
        
        for (AgentExecution exec : activeExecutions) {
            Optional<AgentTask> taskOpt = taskRepository.findById(exec.getTaskId())
                    .filter(task -> workflowRepository.findById(task.getWorkflowId())
                            .map(w -> userId.equals(w.getUserId())).orElse(false));
            if (taskOpt.isPresent()) {
                Map<String, Object> item = new HashMap<>();
                item.put("agentId", exec.getAgentId());
                item.put("taskType", taskOpt.get().getTaskType());
                item.put("status", "RUNNING");
                exList.add(item);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("activeAgents", exList.size());
        response.put("executions", exList);
        return response;
    }
}
