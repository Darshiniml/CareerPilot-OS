package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.Experience;
import com.careerpilot.backend.modules.profile.repositories.ExperienceRepository;
import com.careerpilot.shared.dto.profile.ExperienceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final UserRepository userRepository;

    public ExperienceService(ExperienceRepository experienceRepository, UserRepository userRepository) {
        this.experienceRepository = experienceRepository;
        this.userRepository = userRepository;
    }

    public Page<Experience> getExperience(UUID userId, Pageable pageable) {
        return experienceRepository.findByUserId(userId, pageable);
    }

    public List<Experience> getExperienceList(UUID userId) {
        return experienceRepository.findByUserId(userId);
    }

    @Transactional
    public Experience addExperience(UUID userId, ExperienceDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        validateDates(dto.getStartDate(), dto.getEndDate(), dto.isCurrentJob());

        Experience experience = Experience.builder()
                .id(UUID.randomUUID())
                .user(user)
                .companyName(dto.getCompanyName())
                .title(dto.getTitle())
                .location(dto.getLocation())
                .startDate(dto.getStartDate())
                .endDate(dto.isCurrentJob() ? null : dto.getEndDate())
                .currentJob(dto.isCurrentJob())
                .description(dto.getDescription())
                .build();

        return experienceRepository.save(experience);
    }

    @Transactional
    public void deleteExperience(UUID userId, UUID experienceId) {
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new IllegalArgumentException("Experience record not found"));

        if (!experience.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to experience record");
        }

        experienceRepository.delete(experience);
    }

    private void validateDates(LocalDate start, LocalDate end, boolean currentJob) {
        if (start != null && start.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Start date cannot be in the future");
        }
        if (!currentJob && end == null) {
            throw new IllegalArgumentException("End date is required for past experience");
        }
        if (!currentJob && end != null && end.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("End date cannot be in the future");
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot precede start date");
        }
    }
}
