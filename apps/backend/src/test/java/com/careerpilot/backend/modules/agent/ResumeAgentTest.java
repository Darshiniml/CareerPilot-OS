package com.careerpilot.backend.modules.agent;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.agent.services.ResumeAgent;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.ai.resume.services.ResumeIntelligenceService;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ResumeAgentTest {

    @Test
    public void testResumeAgentExecutionNoResume() {
        ResumeRepository resumeRepository = mock(ResumeRepository.class);
        com.careerpilot.backend.modules.resume.services.ResumeProcessingService processingService =
                mock(com.careerpilot.backend.modules.resume.services.ResumeProcessingService.class);
        com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService knowledgeService =
                mock(com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        ResumeAgent agent = new ResumeAgent(resumeRepository, processingService, knowledgeService, eventPublisher);

        UUID userId = UUID.randomUUID();
        when(resumeRepository.findDefaultByUserId(userId)).thenReturn(Optional.empty());
        when(resumeRepository.findActiveByUserId(userId)).thenReturn(Collections.emptyList());
        
        AgentContext context = AgentContext.builder().userId(userId).build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.BLOCKED, result.getStatus());
        assertTrue(result.getMessage().contains("Resume required"));
        verifyNoInteractions(processingService); // never analyses placeholder text
    }
}
