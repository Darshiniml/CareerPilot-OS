package com.careerpilot.backend.modules.application.services;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DefaultSubmissionAdapter implements SubmissionAdapter {

    @Override
    public SubmissionResult submit(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new SubmissionResult(false, "failed", null, "Payload is empty");
        }

        return new SubmissionResult(false, "manual_action_required", null,
                "No permitted submission provider is configured. Candidate manual action is required.");
    }
}
