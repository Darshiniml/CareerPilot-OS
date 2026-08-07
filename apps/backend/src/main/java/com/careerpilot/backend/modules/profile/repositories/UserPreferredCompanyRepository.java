package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.UserPreferredCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPreferredCompanyRepository extends JpaRepository<UserPreferredCompany, UUID> {
    List<UserPreferredCompany> findByUserId(UUID userId);
    
    @Modifying
    @Query("DELETE FROM UserPreferredCompany c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
