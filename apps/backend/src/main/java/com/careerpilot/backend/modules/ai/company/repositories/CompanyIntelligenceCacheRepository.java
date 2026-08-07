package com.careerpilot.backend.modules.ai.company.repositories;

import com.careerpilot.backend.modules.ai.company.domain.CompanyIntelligenceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyIntelligenceCacheRepository extends JpaRepository<CompanyIntelligenceCache, String> {
}
