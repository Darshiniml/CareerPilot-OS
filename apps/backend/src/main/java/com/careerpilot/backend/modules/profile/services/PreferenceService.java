package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.*;
import com.careerpilot.backend.modules.profile.domain.*;
import com.careerpilot.backend.modules.profile.repositories.*;
import com.careerpilot.shared.dto.profile.PreferencesDto;
import com.careerpilot.shared.events.PreferencesUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final UserPreferredRoleRepository preferredRoleRepository;
    private final UserPreferredLocationRepository preferredLocationRepository;
    private final UserPreferredCompanyRepository preferredCompanyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PreferenceService(
            UserPreferenceRepository userPreferenceRepository,
            UserRepository userRepository,
            UserPreferredRoleRepository preferredRoleRepository,
            UserPreferredLocationRepository preferredLocationRepository,
            UserPreferredCompanyRepository preferredCompanyRepository,
            ApplicationEventPublisher eventPublisher) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.preferredRoleRepository = preferredRoleRepository;
        this.preferredLocationRepository = preferredLocationRepository;
        this.preferredCompanyRepository = preferredCompanyRepository;
        this.eventPublisher = eventPublisher;
    }

    public PreferencesDto getPreferences(UUID userId) {
        UserPreference pref = userPreferenceRepository.findByUserId(userId).orElse(null);
        if (pref == null) {
            return null;
        }

        List<String> roles = preferredRoleRepository.findByUserId(userId).stream()
                .map(UserPreferredRole::getRoleName)
                .collect(Collectors.toList());

        List<String> locations = preferredLocationRepository.findByUserId(userId).stream()
                .map(UserPreferredLocation::getLocationName)
                .collect(Collectors.toList());

        List<String> companies = preferredCompanyRepository.findByUserId(userId).stream()
                .map(UserPreferredCompany::getCompanyName)
                .collect(Collectors.toList());

        return PreferencesDto.builder()
                .workStyle(pref.getWorkStyle())
                .salaryMin(pref.getSalaryMin())
                .salaryMax(pref.getSalaryMax())
                .currencyCode(pref.getCurrencyCode())
                .salaryPeriod(pref.getSalaryPeriod())
                .employmentType(pref.getEmploymentType())
                .jobAlertSettings(pref.isJobAlertSettings())
                .preferredRoles(roles)
                .preferredLocations(locations)
                .preferredCompanies(companies)
                .build();
    }

    @Transactional
    public UserPreference updatePreferences(UUID userId, PreferencesDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.getSalaryMin() != null && dto.getSalaryMax() != null && dto.getSalaryMin() > dto.getSalaryMax()) {
            throw new IllegalArgumentException("Salary minimum cannot exceed salary maximum");
        }

        if (dto.getWorkStyle() != null) {
            WorkStyle.valueOf(dto.getWorkStyle().toUpperCase());
        }
        if (dto.getEmploymentType() != null) {
            EmploymentType.valueOf(dto.getEmploymentType().toUpperCase());
        }

        UserPreference pref = userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserPreference.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .build());

        pref.setWorkStyle(dto.getWorkStyle() != null ? dto.getWorkStyle().toUpperCase() : "REMOTE");
        pref.setSalaryMin(dto.getSalaryMin());
        pref.setSalaryMax(dto.getSalaryMax());
        pref.setCurrencyCode(dto.getCurrencyCode() != null ? dto.getCurrencyCode().toUpperCase() : "USD");
        pref.setSalaryPeriod(dto.getSalaryPeriod() != null ? dto.getSalaryPeriod().toUpperCase() : "YEARLY");
        pref.setEmploymentType(dto.getEmploymentType() != null ? dto.getEmploymentType().toUpperCase() : "FULL_TIME");
        pref.setJobAlertSettings(dto.isJobAlertSettings());

        UserPreference saved = userPreferenceRepository.save(pref);

        preferredRoleRepository.deleteByUserId(userId);
        if (dto.getPreferredRoles() != null) {
            List<UserPreferredRole> roles = dto.getPreferredRoles().stream()
                    .map(role -> UserPreferredRole.builder()
                            .id(UUID.randomUUID())
                            .user(user)
                            .roleName(role)
                            .build())
                    .collect(Collectors.toList());
            preferredRoleRepository.saveAll(roles);
        }

        preferredLocationRepository.deleteByUserId(userId);
        if (dto.getPreferredLocations() != null) {
            List<UserPreferredLocation> locs = dto.getPreferredLocations().stream()
                    .map(loc -> UserPreferredLocation.builder()
                            .id(UUID.randomUUID())
                            .user(user)
                            .locationName(loc)
                            .build())
                    .collect(Collectors.toList());
            preferredLocationRepository.saveAll(locs);
        }

        preferredCompanyRepository.deleteByUserId(userId);
        if (dto.getPreferredCompanies() != null) {
            List<UserPreferredCompany> comps = dto.getPreferredCompanies().stream()
                    .map(comp -> UserPreferredCompany.builder()
                            .id(UUID.randomUUID())
                            .user(user)
                            .companyName(comp)
                            .build())
                    .collect(Collectors.toList());
            preferredCompanyRepository.saveAll(comps);
        }

        PreferencesUpdatedEvent event = PreferencesUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .build();
        eventPublisher.publishEvent(event);

        return saved;
    }
}
