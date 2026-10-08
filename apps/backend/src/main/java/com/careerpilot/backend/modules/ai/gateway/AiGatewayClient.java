package com.careerpilot.backend.modules.ai.gateway;

import com.careerpilot.backend.modules.ai.gateway.exceptions.*;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the CareerPilot AI service.
 *
 * <p>Authenticates with the shared service token ({@code careerpilot.ai-service.token}). Every AI
 * failure is surfaced as an {@link AiServiceException} carrying the AI service's error code; callers
 * must handle it explicitly and never substitute fabricated output.</p>
 */
@Service
@Slf4j
public class AiGatewayClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CONNECT_ATTEMPTS = 3;

    private final RestClient restClient;
    private final String serviceToken;

    public AiGatewayClient(
            @Value("${careerpilot.ai-service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${careerpilot.ai-service.timeout-ms:300000}") int timeoutMs,
            @Value("${careerpilot.ai-service.token:}") String serviceToken) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(timeoutMs);

        this.serviceToken = serviceToken;
        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public AiTaskResponseDto executeTask(AiTaskRequestDto requestDto) {
        return post("/api/v1/ai/execute", requestDto, AiTaskResponseDto.class);
    }

    /** Convenience: run a task and return its {@code result} map. */
    public Map<String, Object> run(String taskType, Map<String, Object> payload) {
        AiTaskResponseDto response = executeTask(AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType(taskType)
                .payload(payload)
                .build());
        if (response == null || response.getResult() == null) {
            throw new AiProviderException("AI service returned no result for " + taskType,
                    "AI_EMPTY_RESULT", 502);
        }
        return response.getResult();
    }

    /** Extract plain text from an uploaded document (PDF, DOCX, TXT). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractText(String fileName, String contentType, byte[] content) {
        Map<String, Object> body = Map.of(
                "fileName", fileName == null ? "document" : fileName,
                "contentType", contentType == null ? "" : contentType,
                "contentBase64", Base64.getEncoder().encodeToString(content));
        return post("/api/v1/ai/documents/extract-text", body, Map.class);
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new AiServiceException(
                    "AI service token is not configured (set AI_SERVICE_TOKEN)", "AI_SERVICE_TOKEN_NOT_CONFIGURED", 503, null);
        }
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        final String cid = correlationId;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return restClient.post()
                        .uri(uri)
                        .header("X-Correlation-ID", cid)
                        .header("Authorization", "Bearer " + serviceToken)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            String raw = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw toException(resp.getStatusCode().value(), raw);
                        })
                        .body(responseType);
            } catch (ResourceAccessException e) {
                // A read timeout means the model is still working or stuck: do not re-run it.
                boolean readTimeout = e.getCause() instanceof SocketTimeoutException ste
                        && ste.getMessage() != null && ste.getMessage().toLowerCase().contains("read timed out");
                if (readTimeout) {
                    throw new AiTimeoutException("AI service did not answer in time", e);
                }
                if (attempt >= MAX_CONNECT_ATTEMPTS) {
                    throw new AiServiceException("AI service is unreachable", "AI_SERVICE_UNREACHABLE", 503, e);
                }
                log.warn("AI service connection failed (attempt {}), retrying: {}", attempt, e.getMessage());
                sleep(500L * attempt);
            } catch (RestClientException e) {
                // A timeout while the response body is being read surfaces as a conversion error.
                if (hasCause(e, SocketTimeoutException.class)) {
                    throw new AiTimeoutException("AI service did not answer in time", e);
                }
                throw new AiServiceException("AI service returned an unreadable response", "AI_SERVICE_ERROR", 502, e);
            }
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null && t.getCause() != t; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    static AiServiceException toException(int status, String rawBody) {
        String code = "AI_SERVICE_ERROR";
        String message = "AI service returned HTTP " + status;
        try {
            JsonNode root = MAPPER.readTree(rawBody);
            JsonNode err = root.has("error") ? root.get("error") : root.path("detail");
            if (err.hasNonNull("code")) {
                code = err.get("code").asText();
            }
            if (err.hasNonNull("message")) {
                message = err.get("message").asText();
            }
        } catch (Exception ignored) {
            // non-JSON error body: keep the generic message
        }
        int mapped = switch (status) {
            case 400, 422 -> 422;           // the AI service rejected the input
            case 401 -> 503;               // service-to-service misconfiguration, not the user's fault
            case 503 -> 503;
            case 504 -> 504;
            default -> 502;
        };
        return new AiProviderException(message, code, mapped);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("Interrupted while retrying the AI service", ie);
        }
    }
}
