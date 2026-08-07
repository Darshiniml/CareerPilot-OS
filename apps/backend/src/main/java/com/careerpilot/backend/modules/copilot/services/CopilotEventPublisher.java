package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.shared.events.BaseEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class CopilotEventPublisher {
    private final ApplicationEventPublisher publisher;

    public CopilotEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(BaseEvent event) {
        publisher.publishEvent(event);
    }

    public <T extends BaseEvent> T build(String type, UUID correlationId) {
        return null;
    }

    public UUID nextId() {
        return UUID.randomUUID();
    }

    public Instant timestamp() {
        return Instant.now();
    }
}
