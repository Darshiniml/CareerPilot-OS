package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationEngine {

    private final PlatformNotificationRepository notificationRepository;

    public String generate(ApplicationRecord application, String message) {
        notificationRepository.save(com.careerpilot.backend.modules.application.domain.PlatformNotification.builder()
                .id(UUID.randomUUID())
                .candidateId(application.getCandidateId())
                .applicationId(application.getApplicationId())
                .type("PLATFORM")
                .title("Application Update")
                .message(message)
                .read(false)
                .createdAt(Instant.now())
                .build());
        return message;
    }
}
