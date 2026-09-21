package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"careerpilot.discovery.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HrCommunicationControllerIntegrationTest {

    private static final String USER_A_EMAIL = "comm-it-a@example.com";
    private static final String USER_B_EMAIL = "comm-it-b@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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
                        .firstName("Comm")
                        .lastName("Tester")
                        .build()).getId());
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("provider", "GMAIL");
        body.put("externalMessageId", "msg-" + UUID.randomUUID());
        body.put("threadId", "thread-" + UUID.randomUUID());
        body.put("sender", "newsletter@unrelated-digest.com");
        body.put("recipient", USER_A_EMAIL);
        body.put("subject", "Weekly tech digest");
        body.put("body", "This week's roundup.");
        body.put("receivedAt", Instant.now().toString());
        return body;
    }

    @Test
    void ingestWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/communications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void ingestDerivesCandidateFromPrincipal() throws Exception {
        mockMvc.perform(post("/api/v1/communications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(userAId.toString()))
                .andExpect(jsonPath("$.processingStatus").value("UNMATCHED"));
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void clientSuppliedCandidateIdIsIgnored() throws Exception {
        Map<String, Object> body = validRequest();
        body.put("candidateId", userBId.toString());

        mockMvc.perform(post("/api/v1/communications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(userAId.toString()));
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void serverOwnedFieldsAreNotInjectable() throws Exception {
        Map<String, Object> body = validRequest();
        body.put("classification", "OFFER");
        body.put("processingStatus", "PROCESSED");
        body.put("matchedApplicationId", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/communications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classification").value("UNKNOWN"))
                .andExpect(jsonPath("$.processingStatus").value("UNMATCHED"))
                .andExpect(jsonPath("$.matchedApplicationId").value(nullValue()));
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void invalidRequestIsRejected() throws Exception {
        Map<String, Object> body = validRequest();
        body.put("sender", "");
        body.put("subject", "");
        body.put("body", "");

        mockMvc.perform(post("/api/v1/communications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_B_EMAIL, roles = "USER")
    void candidateCannotReadAnotherCandidatesCommunication() throws Exception {
        String response = mockMvc.perform(post("/api/v1/communications")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USER_A_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID commId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/v1/communications/" + commId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER_A_EMAIL, roles = "USER")
    void candidateCanRetrieveOwnCommunication() throws Exception {
        String response = mockMvc.perform(post("/api/v1/communications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID commId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(get("/api/v1/communications/" + commId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commId.toString()))
                .andExpect(jsonPath("$.candidateId").value(userAId.toString()));
    }
}
