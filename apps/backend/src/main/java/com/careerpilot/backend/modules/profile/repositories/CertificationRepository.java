package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.Certification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {
    Page<Certification> findByUserId(UUID userId, Pageable pageable);
    List<Certification> findByUserId(UUID userId);
    Optional<Certification> findByUserIdAndName(UUID userId, String name);
}
