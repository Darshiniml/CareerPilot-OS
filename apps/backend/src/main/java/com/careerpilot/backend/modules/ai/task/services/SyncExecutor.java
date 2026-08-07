package com.careerpilot.backend.modules.ai.task.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.task.domain.AiTask;
import com.careerpilot.backend.modules.ai.task.repositories.AiTaskRepository;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.careerpilot.shared.events.AiTaskCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SyncExecutor implements TaskExecutor {

    private final AiGatewayClient gatewayClient;
    private final AiTaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SyncExecutor(
            AiGatewayClient gatewayClient,
            AiTaskRepository taskRepository,
            ApplicationEventPublisher eventPublisher) {
        this.gatewayClient = gatewayClient;
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void execute(AiTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(Instant.now());
        taskRepository.save(task);

        try {
            AiTaskRequestDto requestDto = AiTaskRequestDto.builder()
                    .taskId(task.getId())
                    .taskType(task.getTaskType())
                    .payload(task.getPayload())
                    .metadata(task.getMetadata())
                    .build();

            AiTaskResponseDto response = gatewayClient.executeTask(requestDto);

            task.setStatus("COMPLETED");
            task.setCompletedAt(Instant.now());
            task.setMetadata(response.getResult());
            taskRepository.save(task);

            AiTaskCompletedEvent event = AiTaskCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .taskId(task.getId())
                    .workflowId(task.getWorkflow().getId())
                    .taskType(task.getTaskType())
                    .status("COMPLETED")
                    .result(response.getResult())
                    .build();
            eventPublisher.publishEvent(event);

        } catch (Exception e) {
            task.setRetryCount(task.getRetryCount() + 1);
            if (task.getRetryCount() >= task.getMaxRetries()) {
                task.setStatus("FAILED");
                task.setCompletedAt(Instant.now());
                task.setFailureReason(e.getMessage());
            } else {
                task.setStatus("QUEUED");
            }
            taskRepository.save(task);

            AiTaskCompletedEvent event = AiTaskCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .taskId(task.getId())
                    .workflowId(task.getWorkflow().getId())
                    .taskType(task.getTaskType())
                    .status(task.getStatus())
                    .build();
            eventPublisher.publishEvent(event);
        }
    }
}
