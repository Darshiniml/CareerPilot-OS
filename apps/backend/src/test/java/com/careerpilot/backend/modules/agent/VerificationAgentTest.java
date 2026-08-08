package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.VerificationAgent;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VerificationAgentTest {

    @Test
    public void testVerificationAgentSuccess() {
        ApplicationRecordRepository applicationRepository = mock(ApplicationRecordRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        VerificationAgent agent = new VerificationAgent(applicationRepository, eventPublisher);

        UUID userId = UUID.randomUUID();
        when(applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .correlationId(UUID.randomUUID().toString())
                .build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.SUCCESS, result.getStatus());
    }
}
