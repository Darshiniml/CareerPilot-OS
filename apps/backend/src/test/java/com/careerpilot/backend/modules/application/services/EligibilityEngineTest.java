package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.EligibilityDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEngineTest {

    private final EligibilityEngine engine = new EligibilityEngine();

    @Test
    void marksDuplicateAndUnauthorizedApplicationsAsNotEligible() {
        EligibilityEngine.Result result = engine.evaluate(new EligibilityEngine.Request(
                0.92,
                0.85,
                java.util.List.of("java", "spring"),
                java.util.List.of("java", "spring"),
                3.0,
                4.0,
                true,
                false,
                true,
                true,
                true,
                true
        ));

        assertThat(result.decision()).isEqualTo(EligibilityDecision.NOT_ELIGIBLE);
        assertThat(result.reasons()).contains("Duplicate application", "Work authorization not met");
    }

    @Test
    void requiresReviewWhenLocationOrRemotePreferencesAreUnclear() {
        EligibilityEngine.Result result = engine.evaluate(new EligibilityEngine.Request(
                0.95,
                0.8,
                java.util.List.of("java"),
                java.util.List.of("java"),
                2.0,
                3.0,
                true,
                true,
                false,
                false,
                false,
                true
        ));

        assertThat(result.decision()).isEqualTo(EligibilityDecision.REQUIRES_REVIEW);
        assertThat(result.reasons()).contains("Location requires review", "Remote preference requires review");
    }
}
