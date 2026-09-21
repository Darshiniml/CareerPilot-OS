package com.careerpilot.backend.modules.communication.ingestion.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngestionTokenValidatorTest {

    @Test
    void disabledWhenNoSecretConfiguredAndRejectsAllTokens() {
        IngestionTokenValidator validator = new IngestionTokenValidator("");
        assertFalse(validator.isEnabled());
        assertThrows(IngestionDisabledException.class, () -> validator.validate("anything"));
        assertThrows(IngestionDisabledException.class, () -> validator.validate(null));
    }

    @Test
    void blankSecretIsTreatedAsDisabled() {
        IngestionTokenValidator validator = new IngestionTokenValidator("   ");
        assertFalse(validator.isEnabled());
        assertThrows(IngestionDisabledException.class, () -> validator.validate("x"));
    }

    @Test
    void enabledValidatorRejectsMissingAndIncorrectTokens() {
        IngestionTokenValidator validator = new IngestionTokenValidator("s3cr3t");
        assertTrue(validator.isEnabled());
        assertThrows(IngestionUnauthorizedException.class, () -> validator.validate(null));
        assertThrows(IngestionUnauthorizedException.class, () -> validator.validate("wrong"));
        assertThrows(IngestionUnauthorizedException.class, () -> validator.validate("s3cr3"));
    }

    @Test
    void enabledValidatorAcceptsExactToken() {
        IngestionTokenValidator validator = new IngestionTokenValidator("s3cr3t");
        assertDoesNotThrow(() -> validator.validate("s3cr3t"));
    }
}
