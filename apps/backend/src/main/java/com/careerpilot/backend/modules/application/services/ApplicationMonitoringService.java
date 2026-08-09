package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.PlatformNotification;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class ApplicationMonitoringService {

    public static final Duration STALE_APPROVAL_THRESHOLD = Duration.ofHours(48);
    public static final Duration STALE_VERIFICATION_THRESHOLD = Duration.ofHours(24);
    public static final Duration STALE_REVIEW_THRESHOLD = Duration.ofDays(14);

    private final ApplicationRecordRepository applicationRepository;
    private final PlatformNotificationRepository notificationRepository;

    public ApplicationMonitoringService(ApplicationRecordRepository applicationRepository, PlatformNotificationRepository notificationRepository) {
        this.applicationRepository = applicationRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public MonitoringSummary scanCandidateApplications(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Instant now = Instant.now();

        List<StaleApplicationAlert> alerts = new ArrayList<>();
        int pendingApproval = 0;
        int manualAction = 0;

        for (ApplicationRecord app : apps) {
            if (app.getWorkflowState() == WorkflowState.READY_FOR_APPROVAL) {
                pendingApproval++;
                if (Duration.between(app.getUpdatedAt(), now).compareTo(STALE_APPROVAL_THRESHOLD) > 0) {
                    alerts.add(new StaleApplicationAlert(app.getApplicationId(), app.getWorkflowState().name(), "Awaiting approval for > 48h"));
                }
            } else if (app.getWorkflowState() == WorkflowState.MANUAL_ACTION_REQUIRED) {
                manualAction++;
            } else if (app.getWorkflowState() == WorkflowState.VERIFICATION_PENDING) {
                if (Duration.between(app.getUpdatedAt(), now).compareTo(STALE_VERIFICATION_THRESHOLD) > 0) {
                    alerts.add(new StaleApplicationAlert(app.getApplicationId(), app.getWorkflowState().name(), "Verification pending > 24h"));
                }
            }
        }

        return MonitoringSummary.builder()
                .candidateId(candidateId)
                .totalApplications(apps.size())
                .pendingApprovalCount(pendingApproval)
                .manualActionRequiredCount(manualAction)
                .staleAlerts(alerts)
                .scannedAt(now)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonitoringSummary {
        private UUID candidateId;
        private int totalApplications;
        private int pendingApprovalCount;
        private int manualActionRequiredCount;
        private List<StaleApplicationAlert> staleAlerts;
        private Instant scannedAt;
    }

    @Data
    @AllArgsConstructor
    public static class StaleApplicationAlert {
        private UUID applicationId;
        private String state;
        private String reason;
    }
}
