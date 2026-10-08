package com.careerpilot.backend.modules.followup.repositories;

import com.careerpilot.backend.modules.followup.domain.EmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailConnectionRepository extends JpaRepository<EmailConnection, UUID> {
    List<EmailConnection> findByUserId(UUID userId);

    Optional<EmailConnection> findByUserIdAndProvider(UUID userId, String provider);
}
