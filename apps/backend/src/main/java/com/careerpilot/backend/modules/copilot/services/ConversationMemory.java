package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.CopilotInteraction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationMemory {
    private final List<CopilotInteraction> history = new ArrayList<>();

    public void record(CopilotInteraction interaction) {
        history.add(interaction);
    }

    public List<CopilotInteraction> getHistory() {
        return history;
    }

    public void clear() {
        history.clear();
    }
}
