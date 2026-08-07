package com.careerpilot.backend.modules.ai.events;

import com.careerpilot.backend.modules.ai.task.domain.*;
import com.careerpilot.backend.modules.ai.task.repositories.*;
import com.careerpilot.backend.modules.ai.task.services.AiTaskDispatcher;
import com.careerpilot.shared.events.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class AiEventConsumer {

    private final AiWorkflowRepository workflowRepository;
    private final AiTaskRepository taskRepository;
    private final AiTaskDispatcher taskDispatcher;

    public AiEventConsumer(
            AiWorkflowRepository workflowRepository,
            AiTaskRepository taskRepository,
            AiTaskDispatcher taskDispatcher) {
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
        this.taskDispatcher = taskDispatcher;
    }

    @EventListener
    @Transactional
    public void handleResumeUploaded(ResumeUploadedEvent event) {
        log.info("Received ResumeUploadedEvent for resume ID: {} and user ID: {}", event.getResumeId(), event.getUserId());

        AiWorkflow workflow = AiWorkflow.builder()
                .id(UUID.randomUUID())
                .name("Resume Upload Processing - " + event.getResumeId())
                .status("CREATED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        workflowRepository.save(workflow);

        Map<String, Object> payload = new HashMap<>();
        payload.put("resumeId", event.getResumeId().toString());
        payload.put("userId", event.getUserId().toString());
        payload.put("fileUrl", event.getFileUrl());
        payload.put("fileName", event.getFileName());

        AiTask task = AiTask.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .taskType("RESUME_PARSE")
                .status("CREATED")
                .priority("HIGH")
                .correlationId(event.getCorrelationId())
                .createdBy(event.getUserId())
                .payload(payload)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
        taskRepository.save(task);

        taskDispatcher.dispatch(task);
    }

    @EventListener
    @Transactional
    public void handleResumeDeleted(ResumeDeletedEvent event) {
        log.info("Received ResumeDeletedEvent for resume ID: {} and user ID: {}", event.getResumeId(), event.getUserId());

        AiWorkflow workflow = AiWorkflow.builder()
                .id(UUID.randomUUID())
                .name("Resume Deletion Sync - " + event.getResumeId())
                .status("CREATED")
                .build();
        workflowRepository.save(workflow);

        Map<String, Object> payload = new HashMap<>();
        payload.put("resumeId", event.getResumeId().toString());
        payload.put("userId", event.getUserId().toString());

        AiTask task = AiTask.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .taskType("GENERATE_EMBEDDINGS")
                .status("CREATED")
                .priority("MEDIUM")
                .correlationId(event.getCorrelationId())
                .createdBy(event.getUserId())
                .payload(payload)
                .metadata(new HashMap<>())
                .build();
        taskRepository.save(task);

        taskDispatcher.dispatch(task);
    }

    @EventListener
    @Transactional
    public void handlePreferencesUpdated(PreferencesUpdatedEvent event) {
        log.info("Received PreferencesUpdatedEvent for user ID: {}", event.getUserId());

        AiWorkflow workflow = AiWorkflow.builder()
                .id(UUID.randomUUID())
                .name("Preferences Match Processing - " + event.getUserId())
                .status("CREATED")
                .build();
        workflowRepository.save(workflow);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", event.getUserId().toString());

        AiTask task = AiTask.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .taskType("JOB_MATCH")
                .status("CREATED")
                .priority("LOW")
                .correlationId(event.getCorrelationId())
                .createdBy(event.getUserId())
                .payload(payload)
                .metadata(new HashMap<>())
                .build();
        taskRepository.save(task);

        taskDispatcher.dispatch(task);
    }
}
