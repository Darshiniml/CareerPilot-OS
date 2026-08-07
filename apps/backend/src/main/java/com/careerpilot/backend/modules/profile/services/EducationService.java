package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.Education;
import com.careerpilot.backend.modules.profile.repositories.EducationRepository;
import com.careerpilot.shared.dto.profile.EducationDto;
import com.careerpilot.shared.events.EducationAddedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class EducationService {

    private final EducationRepository educationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EducationService(
            EducationRepository educationRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.educationRepository = educationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<Education> getEducation(UUID userId, Pageable pageable) {
        return educationRepository.findByUserId(userId, pageable);
    }

    public List<Education> getEducationList(UUID userId) {
        return educationRepository.findByUserId(userId);
    }

    @Transactional
    public Education addEducation(UUID userId, EducationDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        validateDates(dto.getStartDate(), dto.getEndDate());

        Education education = Education.builder()
                .id(UUID.randomUUID())
                .user(user)
                .institution(dto.getInstitution())
                .degree(dto.getDegree())
                .fieldOfStudy(dto.getFieldOfStudy())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .description(dto.getDescription())
                .build();

        Education saved = educationRepository.save(education);

        EducationAddedEvent event = EducationAddedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .educationId(saved.getId())
                .build();
        eventPublisher.publishEvent(event);

        return saved;
    }

    @Transactional
    public void deleteEducation(UUID userId, UUID educationId) {
        Education education = educationRepository.findById(educationId)
                .orElseThrow(() -> new IllegalArgumentException("Education record not found"));

        if (!education.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to education record");
        }

        educationRepository.delete(education);
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && start.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Start date cannot be in the future");
        }
        if (end != null && end.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Graduation date cannot be in the future");
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot precede start date");
        }
    }
}
