package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiTimeoutException;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.communication.services.HrCommunicationService;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the candidate-scoped classification endpoints. The AI gateway is replaced
 * with a mock (the project's standard test adapter approach) so no live AI provider is needed, while
 * real persisted users and communications exercise the full web + service + JPA stack.
 */
@SpringBootTest(properties = {"careerpilot.discovery.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunicationClassificationControllerIntegrationTest {

    private static final String USER_A_EMAIL = "classify-it-a@example.com";
    private static final String USER_B_EMAIL = "classify-it-b@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HrCommunicationService communicationService;

    @Autowired
    private HrCommunicationRepository communicationRepository;

    @MockBean
    private AiGatewayClient aiGatewayClient;

    private UUID userAId;
    private UUID userBId;

    @BeforeEach
    void setUp() {
        userAId = fetchOrCreateUser(USER_A_EMAIL);
        userBId = fetchOrCreateUser(USER_B_EMAIL);
    }

    private UUID fetchOrCreateUser(String email) {
        return userRepository.findByEmail(email).map(User::getId).orElseGet(() ->
                userRepository.save(User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .passwordHash("hash")
                        .firstName("Classify")
                        .lastName("Tester")
                        .build()).getId());
    }

    private HrCommunication persistCommunication(UUID candidateId, String recipient, String subject, String body) {
        return communicationService.ingest(
                candidateId,
                CommunicationProvider.GMAIL,
                "msg-" + UUID.randomUUID(),
                "thread-" + UUID.randomUUID(),
                "hr@acme-corp.com",
                recipient,
                subject,
                body,
                Instant.now());
    }

    private void stubAi(String classification, double confidence) {
        Map<String, Object> result = new HashMap<>();
        result.put("classification", classification);
        result.put("confidence", confidence);
        result.put("evidence", "\"invite you to interview\"");
        result.put("reason", "interview scheduling language");
        result.put("signals", List.of("interview"));
        when(aiGatewayClient.executeTask(any(AiTaskRequestDto.class)))
                .thenReturn(AiTaskResponseDto.builder()
                        .taskId(UUID.randomUUID())
                        .status("COMPLETED")
                        .provider("hr-communication-classifier")
                        .result(result)
                        .metadata(new HashMap<>())
                        .build());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void classifyOwnCommunicationPersistsResult() throws Exception {
        HrCommunication communication = persistCommunication(
                userAId, USER_A_EMAIL, "Interview invitation", "We would like to invite you to interview.");
        stubAi("INTERVIEW_INVITATION", 0.92);

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communicationId").value(communication.getId().toString()))
                .andExpect(jsonPath("$.classification").value("INTERVIEW_INVITATION"))
                .andExpect(jsonPath("$.confidence").value(0.92))
                .andExpect(jsonPath("$.processingStatus").value("PROCESSED"));

        HrCommunication reloaded = communicationRepository.findById(communication.getId()).orElseThrow();
        assertEquals(CommunicationClassification.INTERVIEW_INVITATION, reloaded.getClassification());
        assertEquals(CommunicationProcessingStatus.PROCESSED, reloaded.getProcessingStatus());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void retrieveStoredClassification() throws Exception {
        HrCommunication communication = persistCommunication(
                userAId, USER_A_EMAIL, "Offer", "We are pleased to offer you the role.");
        stubAi("OFFER", 0.95);

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/communications/" + communication.getId() + "/classification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("OFFER"))
                .andExpect(jsonPath("$.confidence").value(0.95));
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void repeatedClassificationDoesNotCreateAnotherCommunication() throws Exception {
        HrCommunication communication = persistCommunication(
                userAId, USER_A_EMAIL, "Interview", "We would like to invite you to interview.");
        long before = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userAId).size();
        stubAi("INTERVIEW_INVITATION", 0.9);

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk());

        long after = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userAId).size();
        assertEquals(before, after, "reclassification must not create a new communication");
        assertEquals(1, communicationRepository.findAll().stream()
                .filter(c -> c.getId().equals(communication.getId())).count());
    }

    @Test
    @WithMockUser(username = USER_B_EMAIL, roles = "USER")
    void candidateCannotClassifyAnotherCandidatesCommunication() throws Exception {
        HrCommunication ownedByA = persistCommunication(
                userAId, USER_A_EMAIL, "Interview", "We would like to invite you to interview.");
        stubAi("INTERVIEW_INVITATION", 0.9);

        mockMvc.perform(post("/api/v1/communications/" + ownedByA.getId() + "/classify"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER_B_EMAIL, roles = "USER")
    void candidateCannotRetrieveAnotherCandidatesClassification() throws Exception {
        HrCommunication ownedByA = persistCommunication(
                userAId, USER_A_EMAIL, "Offer", "We are pleased to offer you the role.");

        mockMvc.perform(get("/api/v1/communications/" + ownedByA.getId() + "/classification"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void classifyUnknownCommunicationReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/communications/" + UUID.randomUUID() + "/classify"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void aiUnavailableReturnsHonestErrorAndMarksFailed() throws Exception {
        HrCommunication communication = persistCommunication(
                userAId, USER_A_EMAIL, "Interview", "We would like to invite you to interview.");
        when(aiGatewayClient.executeTask(any(AiTaskRequestDto.class)))
                .thenThrow(new AiTimeoutException("AI service call timed out after 3 attempts"));

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_CLASSIFICATION_FAILED"))
                .andExpect(jsonPath("$.processingStatus").value("FAILED"));

        HrCommunication reloaded = communicationRepository.findById(communication.getId()).orElseThrow();
        assertEquals(CommunicationProcessingStatus.FAILED, reloaded.getProcessingStatus());
        assertEquals(CommunicationClassification.UNKNOWN, reloaded.getClassification());
        assertNull(reloaded.getClassificationConfidence());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void invalidClassificationFromAiFailsSafely() throws Exception {
        HrCommunication communication = persistCommunication(
                userAId, USER_A_EMAIL, "News", "Some update about your candidacy.");
        stubAi("HIRED", 0.9);

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.processingStatus").value("FAILED"));

        HrCommunication reloaded = communicationRepository.findById(communication.getId()).orElseThrow();
        assertEquals(CommunicationClassification.UNKNOWN, reloaded.getClassification());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void requestBodyCannotSupplyClassification() throws Exception {
        HrCommunication communication = persistCommunication(
                userAId, USER_A_EMAIL, "Update", "Thanks for applying, we received your application.");
        stubAi("APPLICATION_RECEIVED", 0.85);

        // The endpoint takes no body; any client-supplied classification/confidence/candidateId is ignored.
        Map<String, Object> ignoredBody = new HashMap<>();
        ignoredBody.put("classification", "OFFER");
        ignoredBody.put("confidence", 1.0);
        ignoredBody.put("candidateId", userBId.toString());

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ignoredBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("APPLICATION_RECEIVED"))
                .andExpect(jsonPath("$.confidence").value(0.85));
    }
}
