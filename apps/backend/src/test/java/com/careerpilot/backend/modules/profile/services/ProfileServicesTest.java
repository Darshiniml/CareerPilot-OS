package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.Education;
import com.careerpilot.backend.modules.profile.domain.Experience;
import com.careerpilot.backend.modules.profile.domain.Project;
import com.careerpilot.backend.modules.profile.repositories.EducationRepository;
import com.careerpilot.backend.modules.profile.repositories.ExperienceRepository;
import com.careerpilot.backend.modules.profile.repositories.ProjectRepository;
import com.careerpilot.shared.dto.profile.EducationDto;
import com.careerpilot.shared.dto.profile.ExperienceDto;
import com.careerpilot.shared.dto.profile.ProjectDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

class ProfileServicesTest {

    private EducationRepository educationRepository;
    private ExperienceRepository experienceRepository;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;

    private EducationService educationService;
    private ExperienceService experienceService;
    private ProjectService projectService;

    private UUID userId;
    private User mockUser;

    @BeforeEach
    void setUp() {
        educationRepository = Mockito.mock(EducationRepository.class);
        experienceRepository = Mockito.mock(ExperienceRepository.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        educationService = new EducationService(educationRepository, userRepository, eventPublisher);
        experienceService = new ExperienceService(experienceRepository, userRepository);
        projectService = new ProjectService(projectRepository, userRepository, eventPublisher);

        userId = UUID.randomUUID();
        mockUser = User.builder().id(userId).email("user@example.com").build();
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    }

    @Test
    void testEducationFutureGraduationYear_ThrowsException() {
        EducationDto dto = EducationDto.builder()
                .institution("MIT")
                .degree("MS")
                .startDate(LocalDate.now().minusYears(2))
                .endDate(LocalDate.now().plusYears(1)) // Future graduation
                .build();

        assertThrows(IllegalArgumentException.class, () -> educationService.addEducation(userId, dto));
    }

    @Test
    void testExperienceEndDateBeforeStartDate_ThrowsException() {
        ExperienceDto dto = ExperienceDto.builder()
                .companyName("Google")
                .title("Engineer")
                .startDate(LocalDate.now().minusYears(1))
                .endDate(LocalDate.now().minusYears(2)) // Invalid order
                .currentJob(false)
                .build();

        assertThrows(IllegalArgumentException.class, () -> experienceService.addExperience(userId, dto));
    }

    @Test
    void testExperienceFutureStartDate_ThrowsException() {
        ExperienceDto dto = ExperienceDto.builder()
                .companyName("Google")
                .title("Engineer")
                .startDate(LocalDate.now().plusYears(1)) // Future start
                .currentJob(true)
                .build();

        assertThrows(IllegalArgumentException.class, () -> experienceService.addExperience(userId, dto));
    }

    @Test
    void testProjectDuplicateName_ThrowsException() {
        ProjectDto dto = ProjectDto.builder()
                .name("CareerPilot")
                .description("AI Platform")
                .build();

        Mockito.when(projectRepository.findByUserIdAndName(userId, "CareerPilot"))
                .thenReturn(Optional.of(Project.builder().name("CareerPilot").build()));

        assertThrows(IllegalArgumentException.class, () -> projectService.addProject(userId, dto));
    }
}
