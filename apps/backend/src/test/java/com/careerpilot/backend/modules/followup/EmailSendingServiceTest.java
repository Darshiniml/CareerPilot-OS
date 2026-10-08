package com.careerpilot.backend.modules.followup;

import com.careerpilot.backend.config.CodedException;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.followup.domain.EmailConnection;
import com.careerpilot.backend.modules.followup.domain.FollowUpDraft;
import com.careerpilot.backend.modules.followup.email.EmailConnectionService;
import com.careerpilot.backend.modules.followup.email.EmailProviderClient;
import com.careerpilot.backend.modules.followup.repositories.EmailConnectionRepository;
import com.careerpilot.backend.modules.followup.repositories.FollowUpDraftRepository;
import com.careerpilot.backend.modules.followup.services.EmailSendingService;
import com.careerpilot.backend.modules.followup.services.FollowUpDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailSendingServiceTest {

    private FollowUpDraftService draftService;
    private FollowUpDraftRepository draftRepository;
    private EmailConnectionRepository connectionRepository;
    private EmailConnectionService connectionService;
    private HrCommunicationRepository communicationRepository;
    private EmailProviderClient gmail;
    private EmailSendingService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID appId = UUID.randomUUID();
    private FollowUpDraft draft;

    @BeforeEach
    void setUp() {
        draftService = mock(FollowUpDraftService.class);
        draftRepository = mock(FollowUpDraftRepository.class);
        connectionRepository = mock(EmailConnectionRepository.class);
        connectionService = mock(EmailConnectionService.class);
        communicationRepository = mock(HrCommunicationRepository.class);
        gmail = mock(EmailProviderClient.class);
        service = new EmailSendingService(draftService, draftRepository, connectionRepository, connectionService,
                communicationRepository, Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC));
        draft = FollowUpDraft.builder().id(UUID.randomUUID()).userId(userId).applicationId(appId)
                .draftType("APPLICATION_FOLLOW_UP").subject("Following up on my application")
                .body("Dear Priya, I wanted to follow up on my application.").status(FollowUpDraft.DRAFT)
                .recipient("Priya <priya@acme.example>").build();
        when(draftService.owned(userId, draft.getId())).thenReturn(draft);
        when(draftRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(draftRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        HrCommunication email = new HrCommunication();
        email.setMatchedApplicationId(appId);
        email.setSender("Priya <PRIYA@acme.example>");
        when(communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId)).thenReturn(List.of(email));
        when(connectionService.client("GMAIL")).thenReturn(gmail);
    }

    private void connected() {
        when(connectionRepository.findByUserId(userId)).thenReturn(List.of(EmailConnection.builder()
                .provider("GMAIL").status(EmailConnection.CONNECTED).encryptedRefreshToken("x").build()));
        when(connectionService.accessToken(any())).thenReturn("access-token");
    }

    @Test
    void approvalRequiresAllPlaceholdersFilled() {
        draft.setBody("Dear [Recruiter Name], following up.");
        CodedException ex = assertThrows(CodedException.class, () -> service.approve(userId, draft.getId()));
        assertEquals("DRAFT_HAS_PLACEHOLDERS", ex.getCode());
        assertEquals(FollowUpDraft.DRAFT, draft.getStatus());
    }

    @Test
    void approvalRejectsRecipientsWhoNeverEmailedAboutTheApplication() {
        draft.setRecipient("stranger@example.org");
        CodedException ex = assertThrows(CodedException.class, () -> service.approve(userId, draft.getId()));
        assertEquals("RECIPIENT_NOT_ALLOWED", ex.getCode());
    }

    @Test
    void approvalRecordsAnAuditEvent() {
        FollowUpDraft approved = service.approve(userId, draft.getId());
        assertEquals(FollowUpDraft.APPROVED, approved.getStatus());
        assertEquals("priya@acme.example", approved.getRecipient());
        verify(draftService).event(eq(draft), eq("APPROVED"), isNull(), anyString());
    }

    @Test
    void cannotSendWithoutApproval() {
        connected();
        assertThrows(IllegalStateException.class, () -> service.send(userId, draft.getId(), null));
        verify(gmail, never()).send(any(), any(), any(), any());
    }

    @Test
    void cannotSendWithoutAConnectedMailbox() {
        draft.setStatus(FollowUpDraft.APPROVED);
        when(connectionRepository.findByUserId(userId)).thenReturn(List.of());
        CodedException ex = assertThrows(CodedException.class, () -> service.send(userId, draft.getId(), null));
        assertEquals("NO_EMAIL_CONNECTION", ex.getCode());
    }

    @Test
    void approvedDraftIsSentExactlyAsApproved() {
        draft.setStatus(FollowUpDraft.APPROVED);
        connected();
        when(gmail.send("access-token", "priya@acme.example", draft.getSubject(), draft.getBody())).thenReturn("msg-123");

        FollowUpDraft sent = service.send(userId, draft.getId(), null);

        assertEquals(FollowUpDraft.SENT, sent.getStatus());
        assertEquals("msg-123", sent.getProviderMessageId());
        assertNotNull(sent.getSentAt());
        verify(draftService).event(eq(draft), eq("SENT"), eq("GMAIL"), anyString());
    }

    @Test
    void providerFailureIsRecordedNotHidden() {
        draft.setStatus(FollowUpDraft.APPROVED);
        connected();
        when(gmail.send(any(), any(), any(), any()))
                .thenThrow(new CodedException("Gmail returned HTTP 500", "EMAIL_PROVIDER_ERROR", 502, null));

        FollowUpDraft failed = service.send(userId, draft.getId(), null);

        assertEquals(FollowUpDraft.FAILED, failed.getStatus());
        assertTrue(failed.getSendError().contains("HTTP 500"));
        assertNull(failed.getSentAt());
        verify(draftService).event(eq(draft), eq("FAILED"), eq("GMAIL"), anyString());
    }
}
