package com.careerpilot.backend.modules.ai.job.repositories;

import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobIntelligenceCacheRepository extends JpaRepository<JobIntelligenceCache, String> {
}
