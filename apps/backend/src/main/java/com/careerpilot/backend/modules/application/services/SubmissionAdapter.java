package com.careerpilot.backend.modules.application.services;

import java.util.Map;

public interface SubmissionAdapter {

    SubmissionResult submit(Map<String, Object> payload);

    final class SubmissionResult {
        private final boolean success;
        private final String status;
        private final String externalReference;
        private final String message;

        public SubmissionResult(boolean success, String status, String externalReference, String message) {
            this.success = success;
            this.status = status;
            this.externalReference = externalReference;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStatus() {
            return status;
        }

        public String getExternalReference() {
            return externalReference;
        }

        public String getMessage() {
            return message;
        }
    }
}
