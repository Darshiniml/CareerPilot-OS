package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRouterTest {
    @Test
    void classifiesJobSearchRequests() {
        IntentRouter router = new IntentRouter();
        assertThat(router.classify("Find me backend jobs in London")).isEqualTo(CopilotIntent.JOB_SEARCH);
    }

    @Test
    void defaultsToGeneralHelpForUnknownRequests() {
        IntentRouter router = new IntentRouter();
        assertThat(router.classify("Hello there")).isEqualTo(CopilotIntent.GENERAL_HELP);
    }
}
