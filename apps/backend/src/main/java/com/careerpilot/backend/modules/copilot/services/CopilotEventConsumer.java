package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.shared.events.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class CopilotEventConsumer {
    private final ConversationMemory conversationMemory;

    public CopilotEventConsumer(ConversationMemory conversationMemory) {
        this.conversationMemory = conversationMemory;
    }

    @EventListener
    public void onMatchCompleted(MatchCompletedEvent event) {
        conversationMemory.getHistory().forEach(interaction -> interaction.getActionsExecuted().add("Matched"));
    }

    @EventListener
    public void onApplicationCompleted(ApplicationCompletedEvent event) {
        conversationMemory.getHistory().forEach(interaction -> interaction.getActionsExecuted().add("Tracked Application"));
    }

    @EventListener
    public void onInterviewCompleted(InterviewCompletedEvent event) {
        conversationMemory.getHistory().forEach(interaction -> interaction.getActionsExecuted().add("Interview Prepared"));
    }
}
