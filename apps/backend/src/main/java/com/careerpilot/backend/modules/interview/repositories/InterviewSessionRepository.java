package com.careerpilot.backend.modules.interview.repositories;

import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    List<InterviewSession> findByCandidateId(UUID candidateId);
}
