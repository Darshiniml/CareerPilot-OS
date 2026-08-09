package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ManualApplicationSubmissionConnector implements ApplicationSubmissionConnector {

    @Override
    public String getConnectorId() {
        return "manual-fallback";
    }

    @Override
    public ApplicationSubmissionMode getSubmissionMode() {
        return ApplicationSubmissionMode.MANUAL_REQUIRED;
    }

    @Override
    public boolean isSubmissionSupported() {
        return false;
    }

    @Override
    public SubmissionResult executeSubmission(ApplicationRecord record, Map<String, Object> applicationPackage) {
        log.info("[MANUAL-SUBMISSION] Connector manual-fallback invoked for application={}. Preparing manual action response package.",
                record.getApplicationId());

        Map<String, Object> evidence = new HashMap<>();
        if (applicationPackage != null) {
            evidence.putAll(applicationPackage);
        }
        evidence.put("requiresManualAction", true);
        evidence.put("reason", "No automated submission API configured or permitted for target job source. Candidate direct apply URL provided.");

        String applyUrl = record.getMetadata() != null && record.getMetadata().containsKey("applyUrl")
                ? String.valueOf(record.getMetadata().get("applyUrl"))
                : "";

        return SubmissionResult.builder()
                .success(false)
                .statusMessage("MANUAL_ACTION_REQUIRED")
                .failureReason("Automated submission API not supported by target source. Direct candidate application required.")
                .confirmationUrl(applyUrl)
                .mode(ApplicationSubmissionMode.MANUAL_REQUIRED)
                .timestamp(Instant.now())
                .evidencePayload(evidence)
                .build();
    }
}
