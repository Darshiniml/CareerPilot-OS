package com.careerpilot.backend.modules.ai.events;

import com.careerpilot.backend.modules.ai.task.domain.AiTask;
import com.careerpilot.backend.modules.ai.task.domain.AiWorkflow;
import com.careerpilot.backend.modules.ai.task.repositories.AiTaskRepository;
import com.careerpilot.backend.modules.ai.task.repositories.AiWorkflowRepository;
import com.careerpilot.backend.modules.ai.task.services.AiTaskDispatcher;
import com.careerpilot.shared.events.ResumeUploadedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiEventConsumerTest {

    private AiWorkflowRepository workflowRepository;
    private AiTaskRepository taskRepository;
    private AiTaskDispatcher taskDispatcher;
    
    private AiEventConsumer eventConsumer;

    @BeforeEach
    void setUp() {
        workflowRepository = Mockito.mock(AiWorkflowRepository.class);
        taskRepository = Mockito.mock(AiTaskRepository.class);
        taskDispatcher = Mockito.mock(AiTaskDispatcher.class);

        eventConsumer = new AiEventConsumer(
                workflowRepository,
                taskRepository,
                taskDispatcher
        );
    }

    @Test
    void testHandleResumeUploaded_PersistsWorkflowAndTaskAndDispatches() {
        ResumeUploadedEvent event = ResumeUploadedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(UUID.randomUUID())
                .resumeId(UUID.randomUUID())
                .fileName("cv.pdf")
                .fileUrl("http://s3/cv.pdf")
                .contentType("application/pdf")
                .fileSize(1000)
                .build();

        eventConsumer.handleResumeUploaded(event);

        // Assert parent workflow saved
        verify(workflowRepository, times(1)).save(any(AiWorkflow.class));
        
        // Assert child task saved
        verify(taskRepository, times(1)).save(any(AiTask.class));
        
        // Assert task dispatched
        verify(taskDispatcher, times(1)).dispatch(any(AiTask.class));
    }
}
