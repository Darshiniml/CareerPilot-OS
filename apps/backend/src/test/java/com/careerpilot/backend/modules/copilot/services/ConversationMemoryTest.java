package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotInteraction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryTest {
    @Test
    void storesStructuredInteractions() {
        ConversationMemory memory = new ConversationMemory();
        CopilotInteraction interaction = new CopilotInteraction();
        interaction.setIntent("job_search");
        memory.record(interaction);

        assertThat(memory.getHistory()).hasSize(1);
    }
}
