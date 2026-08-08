package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.SkillGap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SkillGapRepository extends JpaRepository<SkillGap, UUID> {
    List<SkillGap> findByCandidateId(UUID candidateId);
    void deleteByCandidateId(UUID candidateId);
}
