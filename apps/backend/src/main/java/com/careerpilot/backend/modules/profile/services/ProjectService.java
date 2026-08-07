package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.Project;
import com.careerpilot.backend.modules.profile.repositories.ProjectRepository;
import com.careerpilot.shared.dto.profile.ProjectDto;
import com.careerpilot.shared.events.ProjectAddedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<Project> getProjects(UUID userId, Pageable pageable) {
        return projectRepository.findByUserId(userId, pageable);
    }

    public List<Project> getProjectsList(UUID userId) {
        return projectRepository.findByUserId(userId);
    }

    @Transactional
    public Project addProject(UUID userId, ProjectDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (projectRepository.findByUserIdAndName(userId, dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Project with name '" + dto.getName() + "' already exists");
        }

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name(dto.getName())
                .description(dto.getDescription())
                .url(dto.getUrl())
                .role(dto.getRole())
                .build();

        Project saved = projectRepository.save(project);

        ProjectAddedEvent event = ProjectAddedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .userId(userId)
                .projectId(saved.getId())
                .build();
        eventPublisher.publishEvent(event);

        return saved;
    }

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!project.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to project");
        }

        projectRepository.delete(project);
    }
}
