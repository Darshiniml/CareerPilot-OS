package com.careerpilot.backend.modules.communication.adapters.in.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the ingestion webhook fails closed: when no shared secret is configured the endpoint is
 * disabled and rejects every request (503), even one carrying a plausible token.
 */
@SpringBootTest(properties = {
        "careerpilot.discovery.enabled=false",
        "careerpilot.ingestion.n8n.shared-secret="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class N8nIngestionDisabledIntegrationTest {

    private static final String INGEST_PATH = "/api/v1/communications/ingest/n8n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestionIsDisabledWhenSecretNotConfigured() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("externalMessageId", "msg-" + UUID.randomUUID());
        body.put("sender", "hr@somecompany.com");
        body.put("recipient", "someone@example.com");
        body.put("subject", "Hello");
        body.put("body", "Body");
        body.put("receivedAt", Instant.now().toString());

        mockMvc.perform(post(INGEST_PATH)
                        .header("X-Ingestion-Token", "anything")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isServiceUnavailable());
    }
}
