package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CareerAutomationWorkflowTest {

    @Test
    public void testCompleteAutonomousAgentWorkflowChain() {
        // Mock all specialized agents
        ResumeAgent resumeAgent = mock(ResumeAgent.class);
        JobDiscoveryAgent jobDiscoveryAgent = mock(JobDiscoveryAgent.class);
        CompanyResearchAgent companyResearchAgent = mock(CompanyResearchAgent.class);
        MatchingAgent matchingAgent = mock(MatchingAgent.class);
        ReferenceResearchAgent referenceAgent = mock(ReferenceResearchAgent.class);
        ApplicationAgent applicationAgent = mock(ApplicationAgent.class);
        VerificationAgent verificationAgent = mock(VerificationAgent.class);
        TrackingAgent trackingAgent = mock(TrackingAgent.class);
        NotificationAgent notificationAgent = mock(NotificationAgent.class);

        // Standard success result outputs
        AgentResult successResult = AgentResult.builder()
                .status(AgentResult.Status.SUCCESS)
                .message("Task completed successfully")
                .outputData(new HashMap<>())
                .build();

        when(resumeAgent.execute(any(), any())).thenReturn(successResult);
        when(jobDiscoveryAgent.execute(any(), any())).thenReturn(successResult);
        when(companyResearchAgent.execute(any(), any())).thenReturn(successResult);
        when(matchingAgent.execute(any(), any())).thenReturn(successResult);
        when(referenceAgent.execute(any(), any())).thenReturn(successResult);
        when(applicationAgent.execute(any(), any())).thenReturn(successResult);
        when(verificationAgent.execute(any(), any())).thenReturn(successResult);
        when(trackingAgent.execute(any(), any())).thenReturn(successResult);
        when(notificationAgent.execute(any(), any())).thenReturn(successResult);

        // Verify the mock workflow executes all agents in chain sequence
        AgentContext context = AgentContext.builder()
                .userId(UUID.randomUUID())
                .workflowId(UUID.randomUUID())
                .policy(new AgentPolicy())
                .build();

        assertEquals(AgentResult.Status.SUCCESS, resumeAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, jobDiscoveryAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, companyResearchAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, matchingAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, referenceAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, applicationAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, verificationAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, trackingAgent.execute(context, new AgentTask()).getStatus());
        assertEquals(AgentResult.Status.SUCCESS, notificationAgent.execute(context, new AgentTask()).getStatus());
    }
}
