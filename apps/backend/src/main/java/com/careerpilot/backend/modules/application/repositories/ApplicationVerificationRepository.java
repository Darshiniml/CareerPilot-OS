package com.careerpilot.backend.modules.application.repositories;

import com.careerpilot.backend.modules.application.domain.ApplicationVerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationVerificationRepository extends JpaRepository<ApplicationVerificationResult, UUID> {
    List<ApplicationVerificationResult> findByApplicationIdOrderByVerifiedAtDesc(UUID applicationId);
}
