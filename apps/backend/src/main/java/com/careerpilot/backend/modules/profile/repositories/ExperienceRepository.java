package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.Experience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExperienceRepository extends JpaRepository<Experience, UUID> {
    Page<Experience> findByUserId(UUID userId, Pageable pageable);
    List<Experience> findByUserId(UUID userId);
}
