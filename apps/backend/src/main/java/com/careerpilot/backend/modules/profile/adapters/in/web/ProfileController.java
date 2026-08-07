package com.careerpilot.backend.modules.profile.adapters.in.web;

import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.services.*;
import com.careerpilot.shared.dto.profile.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile Management", description = "Endpoints for managing candidate profiles, experience, education, and alert preferences")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final PersonalInfoService personalInfoService;
    private final EducationService educationService;
    private final ExperienceService experienceService;
    private final ProjectService projectService;
    private final CertificationService certificationService;
    private final SocialLinkService socialLinkService;
    private final PreferenceService preferenceService;

    public ProfileController(
            UserRepository userRepository,
            ProfileService profileService,
            PersonalInfoService personalInfoService,
            EducationService educationService,
            ExperienceService experienceService,
            ProjectService projectService,
            CertificationService certificationService,
            SocialLinkService socialLinkService,
            PreferenceService preferenceService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.personalInfoService = personalInfoService;
        this.educationService = educationService;
        this.experienceService = experienceService;
        this.projectService = projectService;
        this.certificationService = certificationService;
        this.socialLinkService = socialLinkService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    @Operation(summary = "Get user profile", description = "Retrieves complete candidate profile sections")
    public ResponseEntity<ProfileDto> getProfile(Principal principal) {
        UUID userId = getUserId(principal);
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PutMapping("/personal-info")
    @Operation(summary = "Update personal information", description = "Updates active account name and email details")
    public ResponseEntity<Void> updatePersonalInfo(
            Principal principal,
            @Valid @RequestBody PersonalInfoDto dto) {
        UUID userId = getUserId(principal);
        personalInfoService.updatePersonalInfo(userId, dto.getFirstName(), dto.getLastName(), dto.getEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update alert preferences", description = "Updates salaries, employment, and location settings")
    public ResponseEntity<Void> updatePreferences(
            Principal principal,
            @Valid @RequestBody PreferencesDto dto) {
        UUID userId = getUserId(principal);
        preferenceService.updatePreferences(userId, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/social-links")
    @Operation(summary = "Update social URLs", description = "Maps user repositories or LinkedIn web URLs")
    public ResponseEntity<Void> updateSocialLinks(
            Principal principal,
            @Valid @RequestBody SocialLinksDto dto) {
        UUID userId = getUserId(principal);
        socialLinkService.updateSocialLinks(userId, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/education")
    @Operation(summary = "Get education history", description = "Returns pageable lists of education records")
    public ResponseEntity<Page<EducationDto>> getEducation(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getUserId(principal);
        Page<EducationDto> result = educationService.getEducation(userId, PageRequest.of(page, size))
                .map(edu -> EducationDto.builder()
                        .id(edu.getId())
                        .institution(edu.getInstitution())
                        .degree(edu.getDegree())
                        .fieldOfStudy(edu.getFieldOfStudy())
                        .startDate(edu.getStartDate())
                        .endDate(edu.getEndDate())
                        .description(edu.getDescription())
                        .build());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/education")
    @Operation(summary = "Add education record", description = "Appends a new verified graduation degree")
    public ResponseEntity<Void> addEducation(
            Principal principal,
            @Valid @RequestBody EducationDto dto) {
        UUID userId = getUserId(principal);
        educationService.addEducation(userId, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/education/{id}")
    @Operation(summary = "Delete education record")
    public ResponseEntity<Void> deleteEducation(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        educationService.deleteEducation(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/experience")
    @Operation(summary = "Get experience history")
    public ResponseEntity<Page<ExperienceDto>> getExperience(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getUserId(principal);
        Page<ExperienceDto> result = experienceService.getExperience(userId, PageRequest.of(page, size))
                .map(exp -> ExperienceDto.builder()
                        .id(exp.getId())
                        .companyName(exp.getCompanyName())
                        .title(exp.getTitle())
                        .location(exp.getLocation())
                        .startDate(exp.getStartDate())
                        .endDate(exp.getEndDate())
                        .currentJob(exp.isCurrentJob())
                        .description(exp.getDescription())
                        .build());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/experience")
    @Operation(summary = "Add experience record")
    public ResponseEntity<Void> addExperience(
            Principal principal,
            @Valid @RequestBody ExperienceDto dto) {
        UUID userId = getUserId(principal);
        experienceService.addExperience(userId, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/experience/{id}")
    @Operation(summary = "Delete experience record")
    public ResponseEntity<Void> deleteExperience(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        experienceService.deleteExperience(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/projects")
    @Operation(summary = "Get project records")
    public ResponseEntity<Page<ProjectDto>> getProjects(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getUserId(principal);
        Page<ProjectDto> result = projectService.getProjects(userId, PageRequest.of(page, size))
                .map(proj -> ProjectDto.builder()
                        .id(proj.getId())
                        .name(proj.getName())
                        .description(proj.getDescription())
                        .url(proj.getUrl())
                        .role(proj.getRole())
                        .build());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/projects")
    @Operation(summary = "Add project record")
    public ResponseEntity<Void> addProject(
            Principal principal,
            @Valid @RequestBody ProjectDto dto) {
        UUID userId = getUserId(principal);
        projectService.addProject(userId, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/projects/{id}")
    @Operation(summary = "Delete project record")
    public ResponseEntity<Void> deleteProject(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        projectService.deleteProject(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/certifications")
    @Operation(summary = "Get certification records")
    public ResponseEntity<Page<CertificationDto>> getCertifications(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getUserId(principal);
        Page<CertificationDto> result = certificationService.getCertifications(userId, PageRequest.of(page, size))
                .map(cert -> CertificationDto.builder()
                        .id(cert.getId())
                        .name(cert.getName())
                        .issuingOrganization(cert.getIssuingOrganization())
                        .issueDate(cert.getIssueDate())
                        .expirationDate(cert.getExpirationDate())
                        .credentialId(cert.getCredentialId())
                        .credentialUrl(cert.getCredentialUrl())
                        .build());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/certifications")
    @Operation(summary = "Add certification record")
    public ResponseEntity<Void> addCertification(
            Principal principal,
            @Valid @RequestBody CertificationDto dto) {
        UUID userId = getUserId(principal);
        certificationService.addCertification(userId, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/certifications/{id}")
    @Operation(summary = "Delete certification record")
    public ResponseEntity<Void> deleteCertification(Principal principal, @PathVariable UUID id) {
        UUID userId = getUserId(principal);
        certificationService.deleteCertification(userId, id);
        return ResponseEntity.ok().build();
    }

    private UUID getUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }
}
