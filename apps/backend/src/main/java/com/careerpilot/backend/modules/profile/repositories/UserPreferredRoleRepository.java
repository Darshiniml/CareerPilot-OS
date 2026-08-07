package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.UserPreferredRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPreferredRoleRepository extends JpaRepository<UserPreferredRole, UUID> {
    List<UserPreferredRole> findByUserId(UUID userId);
    
    @Modifying
    @Query("DELETE FROM UserPreferredRole r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
