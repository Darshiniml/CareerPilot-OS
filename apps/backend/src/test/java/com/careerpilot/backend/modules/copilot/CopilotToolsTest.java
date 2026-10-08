package com.careerpilot.backend.modules.copilot;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.job.services.JobContextService;
import com.careerpilot.backend.modules.ai.matching.MatchService;
import com.careerpilot.backend.modules.analytics.repositories.LearningProgressRepository;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.services.ApplicationTimelineService;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.copilot.services.CareerHealthService;
import com.careerpilot.backend.modules.copilot.services.CopilotTools;
import com.careerpilot.backend.modules.copilot.services.FollowUpToolBridge;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CopilotToolsTest {

    private ApplicationRecordRepository applicationRepository;
    private ApplicationTimelineService timelineService;
    private CopilotTools tools;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRecordRepository.class);
        timelineService = mock(ApplicationTimelineService.class);
        tools = new CopilotTools(mock(CandidateKnowledgeService.class), mock(MatchService.class), mock(JobContextService.class),
                mock(DiscoveryJobRepository.class), applicationRepository, timelineService, mock(HrCommunicationRepository.class),
                mock(InterviewCoachService.class), mock(LearningProgressRepository.class), mock(CareerHealthService.class),
                mock(AiGatewayClient.class), mock(FollowUpToolBridge.class),
                mock(com.careerpilot.backend.modules.ai.matching.SkillGapService.class));
    }

    @Test
    void catalogContainsOnlyReadOnlyTools() {
        assertFalse(tools.catalog().isEmpty());
        tools.catalog().forEach(t -> assertTrue(t.name().startsWith("get_") || t.name().startsWith("search_"),
                "unexpected non-read tool: " + t.name()));
    }

    @Test
    void timelineToolCannotReadAnotherUsersApplication() {
        UUID owner = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(
                ApplicationRecord.builder().applicationId(appId).candidateId(owner).build()));

        assertThrows(NoSuchElementException.class,
                () -> tools.execute(UUID.randomUUID(), "get_application_timeline", Map.of("applicationId", appId.toString())));
        verifyNoInteractions(timelineService);
    }

    @Test
    void requiredArgumentsAreEnforcedAndUnknownToolsRejected() {
        assertThrows(IllegalArgumentException.class, () -> tools.execute(UUID.randomUUID(), "get_job_match", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> tools.execute(UUID.randomUUID(), "delete_account", Map.of()));
    }
}
