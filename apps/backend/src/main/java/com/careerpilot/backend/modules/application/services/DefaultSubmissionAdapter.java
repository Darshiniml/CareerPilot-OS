package com.careerpilot.backend.modules.application.services;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class DefaultSubmissionAdapter implements SubmissionAdapter {

    @Override
    public SubmissionResult submit(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new SubmissionResult(false, "failed", null, "Payload is empty");
        }

        String candidateId = String.valueOf(payload.getOrDefault("candidateId", "unknown"));
        String reference = "submission-" + candidateId + "-" + UUID.randomUUID();
        return new SubmissionResult(true, "submitted", reference, "Submission prepared successfully");
    }
}
