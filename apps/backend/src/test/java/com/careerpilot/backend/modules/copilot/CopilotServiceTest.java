package com.careerpilot.backend.modules.copilot;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiProviderException;
import com.careerpilot.backend.modules.copilot.domain.CopilotMessage;
import com.careerpilot.backend.modules.copilot.domain.CopilotToolAudit;
import com.careerpilot.backend.modules.copilot.repositories.CopilotMessageRepository;
import com.careerpilot.backend.modules.copilot.repositories.CopilotToolAuditRepository;
import com.careerpilot.backend.modules.copilot.services.CopilotService;
import com.careerpilot.backend.modules.copilot.services.CopilotTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CopilotServiceTest {

    private CopilotTools tools;
    private AiGatewayClient gateway;
    private CopilotMessageRepository messages;
    private CopilotToolAuditRepository audits;
    private CopilotService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tools = mock(CopilotTools.class);
        gateway = mock(AiGatewayClient.class);
        messages = mock(CopilotMessageRepository.class);
        audits = mock(CopilotToolAuditRepository.class);
        service = new CopilotService(tools, gateway, messages, audits);
        when(tools.catalog()).thenReturn(List.of(new CopilotTools.ToolSpec("get_applications", "apps", List.of())));
        when(tools.exists("get_applications")).thenReturn(true);
        when(tools.exists("get_recent_communications")).thenReturn(true);
        when(messages.findByUserIdOrderByCreatedAtDesc(eq(userId), any())).thenReturn(List.of());
        when(gateway.run(eq("COPILOT_ANSWER"), anyMap())).thenReturn(Map.of(
                "answer", "You have 2 applications under review.", "citations", List.of("get_applications"),
                "suggestedActions", List.of(), "dataGaps", List.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelPlanIsExecutedForTheAuthenticatedUserAndAnswerUsesToolData() {
        when(gateway.run(eq("COPILOT_PLAN"), anyMap())).thenReturn(Map.of("tools", List.of(
                Map.of("name", "get_applications", "arguments", Map.of()),
                Map.of("name", "send_email", "arguments", Map.of("to", "x@y.z")))));
        when(tools.execute(eq(userId), eq("get_applications"), anyMap())).thenReturn(Map.of("total", 2));

        Map<String, Object> response = service.chat(userId, "What is my application status?");

        assertEquals("You have 2 applications under review.", response.get("answer"));
        assertEquals("MODEL", response.get("toolSelection"));
        verify(tools).execute(eq(userId), eq("get_applications"), anyMap());
        verify(tools, never()).execute(any(), eq("send_email"), anyMap()); // not in the allow-list
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(gateway).run(eq("COPILOT_ANSWER"), payload.capture());
        assertTrue(payload.getValue().get("toolResults").toString().contains("total=2"));
        verify(audits).save(argThat((CopilotToolAudit a) -> a.getUserId().equals(userId)
                && a.getToolName().equals("get_applications") && a.isSuccess() && "MODEL".equals(a.getSelectedBy())));
        verify(messages, times(2)).save(any(CopilotMessage.class)); // user turn + assistant turn
    }

    @Test
    void planningFailureFallsBackToKeywordRouting() {
        when(gateway.run(eq("COPILOT_PLAN"), anyMap()))
                .thenThrow(new AiProviderException("down", "AI_PROVIDER_UNAVAILABLE", 503));
        when(tools.execute(eq(userId), anyString(), anyMap())).thenReturn(List.of());

        Map<String, Object> response = service.chat(userId, "Did any recruiter reply to me?");

        assertEquals("FALLBACK", response.get("toolSelection"));
        verify(tools).execute(eq(userId), eq("get_recent_communications"), anyMap());
    }

    @Test
    void toolErrorsAreReportedToTheModelAndAudited() {
        when(gateway.run(eq("COPILOT_PLAN"), anyMap())).thenReturn(Map.of("tools",
                List.of(Map.of("name", "get_applications", "arguments", Map.of()))));
        when(tools.execute(eq(userId), eq("get_applications"), anyMap())).thenThrow(new NoSuchElementException("Application not found"));

        service.chat(userId, "status?");

        verify(audits).save(argThat((CopilotToolAudit a) -> !a.isSuccess() && a.getErrorMessage().contains("not found")));
    }

    @Test
    void aiAnswerFailureIsExplicit() {
        when(gateway.run(eq("COPILOT_PLAN"), anyMap())).thenReturn(Map.of("tools", List.of()));
        when(gateway.run(eq("COPILOT_ANSWER"), anyMap()))
                .thenThrow(new AiProviderException("not configured", "AI_PROVIDER_NOT_CONFIGURED", 503));
        when(tools.execute(eq(userId), anyString(), anyMap())).thenReturn(Map.of());
        AiProviderException ex = assertThrows(AiProviderException.class, () -> service.chat(userId, "hello"));
        assertEquals("AI_PROVIDER_NOT_CONFIGURED", ex.getCode());
    }

    @Test
    void emptyMessagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.chat(userId, "  "));
        verifyNoInteractions(gateway);
    }
}
