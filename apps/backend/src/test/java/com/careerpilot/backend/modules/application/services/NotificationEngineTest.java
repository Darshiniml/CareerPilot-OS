package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEngineTest {

    @Test
    void createsPlatformNotificationsForStateChanges() {
        PlatformNotificationRepository repository = Mockito.mock(PlatformNotificationRepository.class);
        NotificationEngine engine = new NotificationEngine(repository);
        ApplicationRecord application = ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(UUID.randomUUID())
                .workflowState(WorkflowState.SUBMITTED)
                .build();

        String notification = engine.generate(application, "Application Submitted");

        assertThat(notification).contains("Application Submitted");
    }
}
