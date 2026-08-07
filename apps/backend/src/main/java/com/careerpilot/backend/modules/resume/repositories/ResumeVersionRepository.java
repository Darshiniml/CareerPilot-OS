package com.careerpilot.backend.modules.resume.repositories;

import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {
    List<ResumeVersion> findByResumeIdOrderByVersionNumberDesc(UUID resumeId);
}
