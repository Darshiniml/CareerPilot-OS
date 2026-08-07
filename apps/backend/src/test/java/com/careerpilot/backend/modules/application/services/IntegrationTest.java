package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationAuditRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.ApprovalPolicyRepository;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import com.careerpilot.backend.modules.application.repositories.RetryAttemptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTest {

    @Test
    void createsApplicationAndTracksLifecycle() {
        ApplicationRecordRepository repository = Mockito.mock(ApplicationRecordRepository.class);
        ApplicationHistoryRepository historyRepository = Mockito.mock(ApplicationHistoryRepository.class);
        ApplicationAuditRepository auditRepository = Mockito.mock(ApplicationAuditRepository.class);
        RetryAttemptRepository retryRepository = Mockito.mock(RetryAttemptRepository.class);
        PlatformNotificationRepository notificationRepository = Mockito.mock(PlatformNotificationRepository.class);
        ApprovalPolicyRepository policyRepository = Mockito.mock(ApprovalPolicyRepository.class);

        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Mockito.when(repository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(false);
        Mockito.when(repository.save(Mockito.any(ApplicationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationOrchestratorService service = new ApplicationOrchestratorService(
                repository,
                historyRepository,
                auditRepository,
                retryRepository,
                notificationRepository,
                policyRepository,
                new ApplicationWorkflowEngine(),
                new EligibilityEngine(),
                new ResumeSelectionEngine(),
                new ApprovalPolicyEngine(),
                new DefaultSubmissionAdapter(),
                null
        );

        ApplicationRecord created = service.createApplication(candidateId, UUID.randomUUID(), jobId, "connector", Map.of("source", "integration"));

        assertThat(created.getWorkflowState()).isEqualTo(WorkflowState.DISCOVERED);
        assertThat(created.getApplicationId()).isNotNull();
    }
}
