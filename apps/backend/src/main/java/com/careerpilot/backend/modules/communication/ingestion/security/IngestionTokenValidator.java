package com.careerpilot.backend.modules.communication.ingestion.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Minimal machine-to-machine authenticator for the n8n ingestion webhook.
 *
 * <p>The shared secret is supplied exclusively through configuration
 * ({@code careerpilot.ingestion.n8n.shared-secret}, env {@code N8N_INGESTION_SHARED_SECRET});
 * it is never hardcoded. When unset, ingestion is DISABLED and every request is rejected
 * (fail closed). Comparison uses {@link MessageDigest#isEqual} to avoid timing leakage.</p>
 *
 * <p>This is the smallest secure abstraction for the current stage. Production hardening should
 * migrate to HMAC-signed webhook payloads or OAuth2 client-credentials; this class is the single
 * seam where that change lands.</p>
 */
@Component
public class IngestionTokenValidator {

    public static final String TOKEN_HEADER = "X-Ingestion-Token";

    private final String sharedSecret;

    public IngestionTokenValidator(
            @Value("${careerpilot.ingestion.n8n.shared-secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
    }

    public boolean isEnabled() {
        return !sharedSecret.isEmpty();
    }

    public void validate(String presentedToken) {
        if (!isEnabled()) {
            throw new IngestionDisabledException("Communication ingestion is not configured");
        }
        if (presentedToken == null) {
            throw new IngestionUnauthorizedException("Missing ingestion token");
        }
        byte[] presented = presentedToken.getBytes(StandardCharsets.UTF_8);
        byte[] expected = sharedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, expected)) {
            throw new IngestionUnauthorizedException("Invalid ingestion token");
        }
    }
}
