package com.careerpilot.backend.modules.ai.resume.repositories;

import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeIntelligenceCacheRepository extends JpaRepository<ResumeIntelligenceCache, String> {
}
