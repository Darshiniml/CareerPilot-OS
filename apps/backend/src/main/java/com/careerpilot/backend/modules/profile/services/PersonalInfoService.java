package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.shared.events.ProfileUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PersonalInfoService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PersonalInfoService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public User updatePersonalInfo(UUID userId, String firstName, String lastName, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already in use");
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);

        User savedUser = userRepository.save(user);

        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .email(email)
                .build();
        eventPublisher.publishEvent(event);

        return savedUser;
    }
}
