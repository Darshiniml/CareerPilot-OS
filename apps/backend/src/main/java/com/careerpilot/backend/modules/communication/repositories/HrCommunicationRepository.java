package com.careerpilot.backend.modules.communication.repositories;

import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrCommunicationRepository extends JpaRepository<HrCommunication, UUID> {

    Optional<HrCommunication> findByProviderAndExternalMessageId(CommunicationProvider provider, String externalMessageId);

    List<HrCommunication> findByCandidateIdOrderByReceivedAtDesc(UUID candidateId);

    Optional<HrCommunication> findByIdAndCandidateId(UUID id, UUID candidateId);

    long countByCandidateId(UUID candidateId);
}
