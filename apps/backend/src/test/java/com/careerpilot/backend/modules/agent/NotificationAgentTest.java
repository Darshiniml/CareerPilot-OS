package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.NotificationAgent;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationAgentTest {

    @Test
    public void testNotificationAgentSuccess() {
        PlatformNotificationRepository notificationRepository = mock(PlatformNotificationRepository.class);
        ApplicationRecordRepository applicationRepository = mock(ApplicationRecordRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        NotificationAgent agent = new NotificationAgent(notificationRepository, applicationRepository, eventPublisher, new ObjectMapper());

        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(
                ApplicationRecord.builder().applicationId(applicationId).candidateId(userId).build()));
        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().payloadJson("{\"applicationId\":\"" + applicationId + "\"}").build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    public void doesNotCreateNotificationForAnotherCandidatesApplication() {
        PlatformNotificationRepository notificationRepository = mock(PlatformNotificationRepository.class);
        ApplicationRecordRepository applicationRepository = mock(ApplicationRecordRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        NotificationAgent agent = new NotificationAgent(notificationRepository, applicationRepository, eventPublisher, new ObjectMapper());

        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(
                ApplicationRecord.builder().applicationId(applicationId).candidateId(UUID.randomUUID()).build()));

        AgentResult result = agent.execute(AgentContext.builder()
                        .userId(userId).correlationId(UUID.randomUUID().toString()).build(),
                AgentTask.builder().payloadJson("{\"applicationId\":\"" + applicationId + "\"}").build());

        assertEquals(AgentResult.Status.FAILED, result.getStatus());
        verify(notificationRepository, never()).save(any());
    }
}
