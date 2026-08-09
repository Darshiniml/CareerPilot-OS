package com.careerpilot.backend.modules.application.repositories;

import com.careerpilot.backend.modules.application.domain.ApplicationPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationPackageRepository extends JpaRepository<ApplicationPackage, UUID> {
    Optional<ApplicationPackage> findByApplicationId(UUID applicationId);
}
