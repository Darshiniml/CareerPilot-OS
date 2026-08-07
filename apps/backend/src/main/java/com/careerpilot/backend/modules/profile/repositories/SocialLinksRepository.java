package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.SocialLinks;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocialLinksRepository extends JpaRepository<SocialLinks, UUID> {
    Optional<SocialLinks> findByUserId(UUID userId);
}
