package com.careerpilot.backend.modules.ai.resume.repositories;

import com.careerpilot.backend.modules.ai.resume.domain.ResumeValidationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ResumeValidationReportRepository extends JpaRepository<ResumeValidationReport, UUID> {
    Optional<ResumeValidationReport> findByDocumentId(UUID documentId);
}
