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
        AiDocumentRepository documentRepository = mock(AiDocumentRepository.class);
        ResumeIntelligenceService resumeIntelligenceService = mock(ResumeIntelligenceService.class);
        ResumeIntelligenceCacheRepository resumeCacheRepository = mock(ResumeIntelligenceCacheRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        ResumeAgent agent = new ResumeAgent(
                resumeRepository, documentRepository, resumeIntelligenceService, resumeCacheRepository, eventPublisher);

        UUID userId = UUID.randomUUID();
        when(resumeRepository.findDefaultByUserId(userId)).thenReturn(Optional.empty());
        
        AgentContext context = AgentContext.builder().userId(userId).build();
        AgentTask task = AgentTask.builder().build();

        AgentResult result = agent.execute(context, task);
        assertEquals(AgentResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("No active resume found"));
    }
}
