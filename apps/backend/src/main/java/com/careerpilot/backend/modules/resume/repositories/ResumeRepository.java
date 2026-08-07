package com.careerpilot.backend.modules.resume.repositories;

import com.careerpilot.backend.modules.resume.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    
    @Query("SELECT r FROM Resume r WHERE r.user.id = :userId AND r.isDeleted = false")
    List<Resume> findActiveByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT COUNT(r) FROM Resume r WHERE r.user.id = :userId AND r.isDeleted = false AND r.isArchived = false")
    long countActiveResumesByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT r FROM Resume r WHERE r.user.id = :userId AND r.isDefault = true AND r.isDeleted = false")
    Optional<Resume> findDefaultByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT r FROM Resume r WHERE r.id = :id AND r.isDeleted = false")
    Optional<Resume> findActiveById(@Param("id") UUID id);
    
    @Query("SELECT r FROM Resume r WHERE r.user.id = :userId AND r.title = :title AND r.isDeleted = false")
    Optional<Resume> findByUserIdAndTitleAndNotDeleted(@Param("userId") UUID userId, @Param("title") String title);
}
