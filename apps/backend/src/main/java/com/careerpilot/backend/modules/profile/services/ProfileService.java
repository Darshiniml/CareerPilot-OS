package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.*;
import com.careerpilot.shared.dto.profile.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final SocialLinkService socialLinkService;
    private final EducationService educationService;
    private final ExperienceService experienceService;
    private final ProjectService projectService;
    private final CertificationService certificationService;
    private final PreferenceService preferenceService;

    public ProfileService(
            UserRepository userRepository,
            SocialLinkService socialLinkService,
            EducationService educationService,
            ExperienceService experienceService,
            ProjectService projectService,
            CertificationService certificationService,
            PreferenceService preferenceService) {
        this.userRepository = userRepository;
        this.socialLinkService = socialLinkService;
        this.educationService = educationService;
        this.experienceService = experienceService;
        this.projectService = projectService;
        this.certificationService = certificationService;
        this.preferenceService = preferenceService;
    }

    public ProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SocialLinks socialLinks = socialLinkService.getSocialLinks(userId);
        List<Education> eduList = educationService.getEducationList(userId);
        List<Experience> expList = experienceService.getExperienceList(userId);
        List<Project> projList = projectService.getProjectsList(userId);
        List<Certification> certList = certificationService.getCertificationsList(userId);
        PreferencesDto prefDto = preferenceService.getPreferences(userId);

        SocialLinksDto socialLinksDto = socialLinks != null ? SocialLinksDto.builder()
                .id(socialLinks.getId())
                .linkedin(socialLinks.getLinkedin())
                .github(socialLinks.getGithub())
                .portfolio(socialLinks.getPortfolio())
                .twitter(socialLinks.getTwitter())
                .build() : null;

        List<EducationDto> eduDtos = eduList.stream()
                .map(edu -> EducationDto.builder()
                        .id(edu.getId())
                        .institution(edu.getInstitution())
                        .degree(edu.getDegree())
                        .fieldOfStudy(edu.getFieldOfStudy())
                        .startDate(edu.getStartDate())
                        .endDate(edu.getEndDate())
                        .description(edu.getDescription())
                        .build())
                .collect(Collectors.toList());

        List<ExperienceDto> expDtos = expList.stream()
                .map(exp -> ExperienceDto.builder()
                        .id(exp.getId())
                        .companyName(exp.getCompanyName())
                        .title(exp.getTitle())
                        .location(exp.getLocation())
                        .startDate(exp.getStartDate())
                        .endDate(exp.getEndDate())
                        .currentJob(exp.isCurrentJob())
                        .description(exp.getDescription())
                        .build())
                .collect(Collectors.toList());

        List<ProjectDto> projDtos = projList.stream()
                .map(proj -> ProjectDto.builder()
                        .id(proj.getId())
                        .name(proj.getName())
                        .description(proj.getDescription())
                        .url(proj.getUrl())
                        .role(proj.getRole())
                        .build())
                .collect(Collectors.toList());

        List<CertificationDto> certDtos = certList.stream()
                .map(cert -> CertificationDto.builder()
                        .id(cert.getId())
                        .name(cert.getName())
                        .issuingOrganization(cert.getIssuingOrganization())
                        .issueDate(cert.getIssueDate())
                        .expirationDate(cert.getExpirationDate())
                        .credentialId(cert.getCredentialId())
                        .credentialUrl(cert.getCredentialUrl())
                        .build())
                .collect(Collectors.toList());

        List<String> skills = new ArrayList<>();

        return ProfileDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .socialLinks(socialLinksDto)
                .skills(skills)
                .education(eduDtos)
                .experience(expDtos)
                .projects(projDtos)
                .certifications(certDtos)
                .preferences(prefDto)
                .build();
    }
}
