package com.careerpilot.backend.modules.analytics.repositories;

import com.careerpilot.backend.modules.analytics.domain.SkillDemandSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SkillDemandSnapshotRepository extends JpaRepository<SkillDemandSnapshot, UUID> {
    List<SkillDemandSnapshot> findBySkillOrderByCapturedAtAsc(String skill);
}
