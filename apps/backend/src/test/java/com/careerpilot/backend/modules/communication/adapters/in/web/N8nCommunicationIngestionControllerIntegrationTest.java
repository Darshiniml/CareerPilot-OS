package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "careerpilot.discovery.enabled=false",
        "careerpilot.ingestion.n8n.shared-secret=test-ingestion-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class N8nCommunicationIngestionControllerIntegrationTest {

    private static final String INGEST_PATH = "/api/v1/communications/ingest/n8n";
    private static final String TOKEN_HEADER = "X-Ingestion-Token";
    private static final String VALID_TOKEN = "test-ingestion-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HrCommunicationRepository communicationRepository;

    @Autowired
    private DiscoveryJobRepository discoveryJobRepository;

    @Autowired
    private ApplicationRecordRepository applicationRepository;

    private String persistUserEmail(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hash")
                .firstName("N8n")
                .lastName("Tester")
                .build());
        return email;
    }

    private UUID userId(String email) {
        return userRepository.findByEmail(email).map(User::getId).orElseThrow();
    }

    private ApplicationRecord persistApplication(String email, String company, String title, String sourceUrl) {
        UUID candidateId = userId(email);
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
        return applicationRepository.save(ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(candidateId)
                .jobId(job.getId())
                .connectorId(job.getConnectorId())
                .workflowState(WorkflowState.SUBMITTED)
                .createdAt(now)
                .updatedAt(now)
                .retryCount(0)
                .build());
    }

    private Map<String, Object> payload(String recipient) {
        Map<String, Object> body = new HashMap<>();
        body.put("externalMessageId", "msg-" + UUID.randomUUID());
        body.put("threadId", "thread-" + UUID.randomUUID());
        body.put("sender", "newsletter@unrelated-digest.com");
        body.put("recipient", recipient);
        body.put("subject", "Weekly tech digest");
        body.put("body", "This week's roundup.");
        body.put("receivedAt", Instant.now().toString());
        return body;
    }

    // Case 1 + 3: valid authenticated ingestion, provider is N8N.
    @Test
    void validIngestionCreatesCommunicationWithN8nProvider() throws Exception {
        String recipient = persistUserEmail("valid");
        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(recipient))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("N8N"))
                .andExpect(jsonPath("$.candidateId").value(userId(recipient).toString()))
                .andExpect(jsonPath("$.processingStatus").value("UNMATCHED"));
    }

    // Case 4 + 5 + 6: provider-owned fields are persisted verbatim.
    @Test
    void persistsProviderOwnedFields() throws Exception {
        String recipient = persistUserEmail("fields");
        String externalMessageId = "ext-persist-" + UUID.randomUUID();
        Map<String, Object> body = payload(recipient);
        body.put("externalMessageId", externalMessageId);
        body.put("threadId", "thread-persist-1");
        body.put("sender", "hr@somecompany.com");
        body.put("subject", "Regarding your application");
        body.put("body", "We are reviewing your profile.");

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalMessageId").value(externalMessageId))
                .andExpect(jsonPath("$.threadId").value("thread-persist-1"))
                .andExpect(jsonPath("$.sender").value("hr@somecompany.com"))
                .andExpect(jsonPath("$.recipient").value(recipient))
                .andExpect(jsonPath("$.subject").value("Regarding your application"))
                .andExpect(jsonPath("$.body").value("We are reviewing your profile."));
    }

    // Case 7: duplicate provider+externalMessageId for the same candidate is idempotent.
    @Test
    void duplicateIngestionIsIdempotent() throws Exception {
        String recipient = persistUserEmail("idempotent");
        String externalMessageId = "ext-dup-" + UUID.randomUUID();
        Map<String, Object> body = payload(recipient);
        body.put("externalMessageId", externalMessageId);

        String first = mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID firstId = UUID.fromString(objectMapper.readTree(first).get("id").asText());
        UUID secondId = UUID.fromString(objectMapper.readTree(second).get("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                communicationRepository.findByProviderAndExternalMessageId(
                        com.careerpilot.backend.modules.communication.domain.CommunicationProvider.N8N, externalMessageId)
                        .stream().count());
    }

    // Case 8: same externalMessageId belonging to another candidate is rejected (409), not re-attached.
    @Test
    void crossCandidateDuplicateIsRejected() throws Exception {
        String recipientA = persistUserEmail("ownerA");
        String recipientB = persistUserEmail("ownerB");
        String externalMessageId = "ext-cross-" + UUID.randomUUID();
        Map<String, Object> bodyA = payload(recipientA);
        bodyA.put("externalMessageId", externalMessageId);
        Map<String, Object> bodyB = payload(recipientB);
        bodyB.put("externalMessageId", externalMessageId);

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyA)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyB)))
                .andExpect(status().isConflict());

        HrCommunication stored = communicationRepository.findByProviderAndExternalMessageId(
                com.careerpilot.backend.modules.communication.domain.CommunicationProvider.N8N, externalMessageId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(userId(recipientA), stored.getCandidateId());
    }

    // Case 9: a caller-supplied candidateId cannot override recipient-derived identity.
    @Test
    void candidateIdentityCannotBeSpoofed() throws Exception {
        String recipientA = persistUserEmail("spoofTarget");
        String other = persistUserEmail("spoofSource");
        Map<String, Object> body = payload(recipientA);
        body.put("candidateId", userId(other).toString());

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(userId(recipientA).toString()));
    }

    // Case 10 + 11 + 12: server-owned fields cannot be injected.
    @Test
    void serverOwnedFieldsCannotBeInjected() throws Exception {
        String recipient = persistUserEmail("inject");
        Map<String, Object> body = payload(recipient);
        body.put("classification", "OFFER");
        body.put("classificationConfidence", 0.99);
        body.put("matchedApplicationId", UUID.randomUUID().toString());
        body.put("matchConfidence", 1.0);
        body.put("processingStatus", "PROCESSED");

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classification").value("UNKNOWN"))
                .andExpect(jsonPath("$.classificationConfidence").value(nullValue()))
                .andExpect(jsonPath("$.matchedApplicationId").value(nullValue()))
                .andExpect(jsonPath("$.processingStatus").value("UNMATCHED"));
    }

    // Case 13a: missing required external message id is rejected.
    @Test
    void missingExternalMessageIdIsRejected() throws Exception {
        String recipient = persistUserEmail("malformed");
        Map<String, Object> body = payload(recipient);
        body.remove("externalMessageId");

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // Case 13b: invalid timestamp is rejected with 400.
    @Test
    void invalidTimestampIsRejected() throws Exception {
        String recipient = persistUserEmail("badtime");
        Map<String, Object> body = payload(recipient);
        body.put("receivedAt", "yesterday");

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // Case 14a: missing token is unauthorized.
    @Test
    void missingTokenIsUnauthorized() throws Exception {
        String recipient = persistUserEmail("unauth");
        mockMvc.perform(post(INGEST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(recipient))))
                .andExpect(status().isUnauthorized());
    }

    // Case 14b: incorrect token is unauthorized.
    @Test
    void incorrectTokenIsUnauthorized() throws Exception {
        String recipient = persistUserEmail("badtoken");
        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, "not-the-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(recipient))))
                .andExpect(status().isUnauthorized());
    }

    // Unknown recipient is rejected without leaking whether the address exists, and persists nothing.
    @Test
    void unknownRecipientIsRejected() throws Exception {
        Map<String, Object> body = payload("nobody-" + UUID.randomUUID() + "@unknown.test");
        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    // Real application matching through the ingestion path using a real persisted application.
    @Test
    void matchesRealApplicationUsingEvidence() throws Exception {
        String recipient = persistUserEmail("matched");
        ApplicationRecord application = persistApplication(
                recipient, "Acme Corp", "Backend Engineer", "https://jobs.acme-corp.com/123");

        Map<String, Object> body = new HashMap<>();
        body.put("externalMessageId", "msg-" + UUID.randomUUID());
        body.put("threadId", "thread-match");
        body.put("sender", "recruiter@acme-corp.com");
        body.put("recipient", recipient);
        body.put("subject", "Your application for Backend Engineer at Acme Corp");
        body.put("body", "Thanks for applying to the Backend Engineer role.");
        body.put("receivedAt", Instant.now().toString());

        mockMvc.perform(post(INGEST_PATH)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.matchedApplicationId").value(application.getApplicationId().toString()))
                .andExpect(jsonPath("$.classification").value("UNKNOWN"));
    }
}
