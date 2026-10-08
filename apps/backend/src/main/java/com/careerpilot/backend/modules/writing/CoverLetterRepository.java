package com.careerpilot.backend.modules.writing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, UUID> {
    List<CoverLetter> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CoverLetter> findByIdAndUserId(UUID id, UUID userId);
}
