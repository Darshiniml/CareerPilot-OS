package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.NotificationAgent;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationAgentTest {

    @Test
    public void testNotificationAgentSuccess() {
        PlatformNotificationRepository notificationRepository = mock(PlatformNotificationRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        NotificationAgent agent = new NotificationAgent(notificationRepository, eventPublisher);

        UUID userId = UUID.randomUUID();
        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
        verify(notificationRepository, times(1)).save(any());
    }
}
