package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByUserId(UUID userId, Pageable pageable);
    List<Project> findByUserId(UUID userId);
    Optional<Project> findByUserIdAndName(UUID userId, String name);
}
