package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.Education;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EducationRepository extends JpaRepository<Education, UUID> {
    Page<Education> findByUserId(UUID userId, Pageable pageable);
    List<Education> findByUserId(UUID userId);
}
