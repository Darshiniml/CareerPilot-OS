package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"careerpilot.discovery.enabled=false"})
@ActiveProfiles("test")
class HrCommunicationServiceTest {

    @Autowired
    private HrCommunicationService service;

    @Autowired
    private HrCommunicationRepository communicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiscoveryJobRepository discoveryJobRepository;

    @Autowired
    private ApplicationRecordRepository applicationRepository;

    private User persistUser(String email) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hash")
                .firstName("Test")
                .lastName("Candidate")
                .build();
        return userRepository.save(user);
    }

    private ApplicationRecord persistApplication(UUID candidateId, String company, String title, String sourceUrl) {
        DiscoveryJob job = DiscoveryJob.builder()
                .id(UUID.randomUUID())
                .externalId("ext-" + UUID.randomUUID())
                .connectorId("connector-" + UUID.randomUUID())
                .company(company)
                .title(title)
                .sourceUrl(sourceUrl)
                .contentHash("hash-" + UUID.randomUUID())
                .build();
        discoveryJobRepository.save(job);

        Instant now = Instant.now();
        ApplicationRecord application = ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(candidateId)
                .jobId(job.getId())
                .connectorId(job.getConnectorId())
                .workflowState(WorkflowState.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .retryCount(0)
                .build();
        return applicationRepository.save(application);
    }

    @Test
    void ingestPersistsCommunicationWithServerDerivedDefaults() {
        User user = persistUser("defaults-" + UUID.randomUUID() + "@example.com");

        HrCommunication saved = service.ingest(
                user.getId(),
                CommunicationProvider.GMAIL,
                "msg-" + UUID.randomUUID(),
                "thread-1",
                "recruiter@acme-corp.com",
                user.getEmail(),
                "Your application for Backend Engineer at Acme Corp",
                "We received your application.",
                Instant.now());

        assertNotNull(saved.getId());
        assertEquals(user.getId(), saved.getCandidateId());
        assertEquals(CommunicationClassification.UNKNOWN, saved.getClassification());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(communicationRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void ingestMatchesCommunicationToRealApplicationUsingEvidence() {
        User user = persistUser("matched-" + UUID.randomUUID() + "@example.com");
        ApplicationRecord application = persistApplication(
                user.getId(), "Acme Corp", "Backend Engineer", "https://jobs.acme-corp.com/123");

        HrCommunication saved = service.ingest(
                user.getId(),
                CommunicationProvider.GMAIL,
                "msg-" + UUID.randomUUID(),
                "thread-2",
                "recruiter@acme-corp.com",
                user.getEmail(),
                "Your application for Backend Engineer at Acme Corp",
                "Thanks for applying to the Backend Engineer role.",
                Instant.now());

        assertEquals(CommunicationProcessingStatus.PROCESSED, saved.getProcessingStatus());
        assertEquals(application.getApplicationId(), saved.getMatchedApplicationId());
        assertNotNull(saved.getMatchConfidence());
        assertTrue(saved.getMatchConfidence() >= 0.6, "confidence should meet threshold: " + saved.getMatchConfidence());
        assertNotNull(saved.getMatchEvidence());
    }

    @Test
    void ingestLeavesUnrelatedCommunicationUnmatched() {
        User user = persistUser("unmatched-" + UUID.randomUUID() + "@example.com");
        persistApplication(user.getId(), "Acme Corp", "Backend Engineer", "https://jobs.acme-corp.com/123");

        HrCommunication saved = service.ingest(
                user.getId(),
                CommunicationProvider.OUTLOOK,
                "msg-" + UUID.randomUUID(),
                "thread-3",
                "newsletter@unrelated-digest.com",
                user.getEmail(),
                "Weekly tech digest",
                "Here is this week's roundup of articles.",
                Instant.now());

        assertEquals(CommunicationProcessingStatus.UNMATCHED, saved.getProcessingStatus());
        assertNull(saved.getMatchedApplicationId());
        assertNull(saved.getMatchConfidence());
    }

    @Test
    void ingestIsIdempotentForSameCandidateAndMessageIdentity() {
        User user = persistUser("idempotent-" + UUID.randomUUID() + "@example.com");
        String externalId = "msg-" + UUID.randomUUID();

        HrCommunication first = service.ingest(
                user.getId(), CommunicationProvider.GMAIL, externalId, "thread-4",
                "recruiter@acme-corp.com", user.getEmail(), "Subject", "Body", Instant.now());
        HrCommunication second = service.ingest(
                user.getId(), CommunicationProvider.GMAIL, externalId, "thread-4",
                "recruiter@acme-corp.com", user.getEmail(), "Subject", "Body", Instant.now());

        assertEquals(first.getId(), second.getId());
        assertEquals(1, communicationRepository.findByCandidateIdOrderByReceivedAtDesc(user.getId()).size());
    }

    @Test
    void ingestRejectsMessageIdentityOwnedByAnotherCandidate() {
        User owner = persistUser("owner-" + UUID.randomUUID() + "@example.com");
        User other = persistUser("other-" + UUID.randomUUID() + "@example.com");
        String externalId = "msg-" + UUID.randomUUID();

        service.ingest(owner.getId(), CommunicationProvider.GMAIL, externalId, "thread-5",
                "recruiter@acme-corp.com", owner.getEmail(), "Subject", "Body", Instant.now());

        assertThrows(DuplicateCommunicationException.class, () ->
                service.ingest(other.getId(), CommunicationProvider.GMAIL, externalId, "thread-5",
                        "recruiter@acme-corp.com", other.getEmail(), "Subject", "Body", Instant.now()));
    }
}
