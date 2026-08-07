package com.careerpilot.backend.modules.profile.repositories;

import com.careerpilot.backend.modules.profile.domain.UserPreferredLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPreferredLocationRepository extends JpaRepository<UserPreferredLocation, UUID> {
    List<UserPreferredLocation> findByUserId(UUID userId);
    
    @Modifying
    @Query("DELETE FROM UserPreferredLocation l WHERE l.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
