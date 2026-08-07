package com.careerpilot.shared.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private SocialLinksDto socialLinks;
    private List<String> skills;
    private List<EducationDto> education;
    private List<ExperienceDto> experience;
    private List<ProjectDto> projects;
    private List<CertificationDto> certifications;
    private PreferencesDto preferences;
}
