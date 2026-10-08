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

        VerificationAgent agent = new VerificationAgent(applicationRepository,
                mock(com.careerpilot.backend.modules.application.repositories.ApplicationVerificationRepository.class),
                mock(com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository.class),
                eventPublisher);

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

    @Test
    public void submittedApplicationWithoutEvidenceIsReportedUnverified() {
        ApplicationRecordRepository applicationRepository = mock(ApplicationRecordRepository.class);
        var verificationRepository = mock(com.careerpilot.backend.modules.application.repositories.ApplicationVerificationRepository.class);
        var communicationRepository = mock(com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        VerificationAgent agent = new VerificationAgent(applicationRepository, verificationRepository, communicationRepository, eventPublisher);
        UUID userId = UUID.randomUUID();
        var app = com.careerpilot.backend.modules.application.domain.ApplicationRecord.builder()
                .applicationId(UUID.randomUUID()).candidateId(userId).jobId(UUID.randomUUID())
                .workflowState(com.careerpilot.backend.modules.application.domain.WorkflowState.SUBMITTED).build();
        when(applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(app));
        when(communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId)).thenReturn(List.of());
        when(verificationRepository.findByApplicationIdOrderByVerifiedAtDesc(app.getApplicationId())).thenReturn(List.of());

        AgentResult result = agent.execute(AgentContext.builder().userId(userId).build(), AgentTask.builder().build());

        @SuppressWarnings("unchecked")
        var rows = (List<java.util.Map<String, Object>>) result.getOutputData().get("applications");
        assertEquals("UNVERIFIED", rows.get(0).get("evidenceLevel"));
        verify(eventPublisher, never()).publishEvent(any()); // nothing is claimed as verified
    }
}
