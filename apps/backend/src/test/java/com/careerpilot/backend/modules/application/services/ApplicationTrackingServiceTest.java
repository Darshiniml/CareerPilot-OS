package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationVerificationResult;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationVerificationRepository;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationTrackingServiceTest {

    private ApplicationRecordRepository applications;
    private ApplicationHistoryRepository history;
    private ApplicationVerificationRepository verifications;
    private ApplicationTrackingService service;
    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRecordRepository.class);
        history = mock(ApplicationHistoryRepository.class);
        verifications = mock(ApplicationVerificationRepository.class);
        service = new ApplicationTrackingService(applications, history, verifications, mock(PlatformNotificationRepository.class));
        when(applications.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ApplicationRecord application(WorkflowState state) {
        ApplicationRecord app = ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(candidateId)
                .jobId(UUID.randomUUID())
                .workflowState(state)
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(applications.findById(app.getApplicationId())).thenReturn(Optional.of(app));
        return app;
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowState.class, names = {"DISCOVERED", "MATCHED", "ELIGIBLE", "APPLICATION_READY",
            "READY_FOR_APPROVAL", "APPROVED", "MANUAL_ACTION_REQUIRED", "ALREADY_APPLIED"})
    void candidateAttestationMovesPreSubmissionApplicationToSubmitted(WorkflowState from) {
        ApplicationRecord app = application(from);

        ApplicationVerificationResult result = service.verifyApplicationWithEvidence(app.getApplicationId(),
                "CONFIRMATION_EMAIL", "Applied on the company site", null, candidateId);

        assertThat(result.getVerificationStatus()).isEqualTo("USER_ATTESTED");
        assertThat(app.getWorkflowState()).isEqualTo(WorkflowState.SUBMITTED);
        assertThat(app.getSubmittedAt()).isNotNull();
        ArgumentCaptor<ApplicationHistory> row = ArgumentCaptor.forClass(ApplicationHistory.class);
        verify(history).save(row.capture());
        assertThat(row.getValue().getActorType()).isEqualTo(ApplicationHistory.ACTOR_USER);
        assertThat(row.getValue().getFromState()).isEqualTo(from);
    }

    @Test
    void attestationNeverClaimsExternalVerificationOrRewindsProgress() {
        ApplicationRecord app = application(WorkflowState.INTERVIEW);

        ApplicationVerificationResult result = service.verifyApplicationWithEvidence(app.getApplicationId(),
                "CONFIRMATION_EMAIL", "ref", null, candidateId);

        assertThat(result.getVerificationStatus()).isEqualTo("USER_ATTESTED");
        assertThat(app.getWorkflowState()).isEqualTo(WorkflowState.INTERVIEW);
        verify(history, never()).save(any());
    }

    @Test
    void attestationRequiresOwnership() {
        ApplicationRecord app = application(WorkflowState.DISCOVERED);
        assertThatThrownBy(() -> service.verifyApplicationWithEvidence(app.getApplicationId(),
                "CONFIRMATION_EMAIL", "ref", null, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class);
        assertThat(app.getWorkflowState()).isEqualTo(WorkflowState.DISCOVERED);
    }

    @Test
    void preSubmissionStatesCannotJumpToInterviewOrOffer() {
        assertThat(ApplicationTrackingService.isTransitionAllowed(WorkflowState.DISCOVERED, WorkflowState.INTERVIEW)).isFalse();
        assertThat(ApplicationTrackingService.isTransitionAllowed(WorkflowState.DISCOVERED, WorkflowState.OFFER)).isFalse();
        assertThat(ApplicationTrackingService.isTransitionAllowed(WorkflowState.APPROVED, WorkflowState.SUBMITTED_VERIFIED)).isFalse();
    }

    @Test
    void terminalStatesNeverMove() {
        for (WorkflowState terminal : ApplicationTrackingService.TERMINAL_STATES) {
            for (WorkflowState target : WorkflowState.values()) {
                assertThat(ApplicationTrackingService.isTransitionAllowed(terminal, target))
                        .as("%s -> %s", terminal, target).isFalse();
            }
        }
    }
}
