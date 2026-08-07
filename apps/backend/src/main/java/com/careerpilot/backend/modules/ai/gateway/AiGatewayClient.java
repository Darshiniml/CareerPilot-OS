package com.careerpilot.backend.modules.ai.gateway;

import com.careerpilot.backend.modules.ai.gateway.exceptions.*;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
public class AiGatewayClient {

    private final RestClient restClient;

    public AiGatewayClient(
            @Value("${careerpilot.ai-service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${careerpilot.ai-service.timeout-ms:30000}") int timeoutMs) {
        
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public AiTaskResponseDto executeTask(AiTaskRequestDto requestDto) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        int maxRetries = 3;
        int attempt = 0;
        
        while (true) {
            attempt++;
            try {
                return restClient.post()
                        .uri("/api/v1/ai/execute")
                        .header("X-Correlation-ID", correlationId)
                        .header("Authorization", "Bearer mock-token-milestone-3")
                        .body(requestDto)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                            throw new AiProviderException("AI Service returned client error status: " + resp.getStatusCode());
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                            throw new AiProviderException("AI Service returned server error status: " + resp.getStatusCode());
                        })
                        .body(AiTaskResponseDto.class);
            } catch (ResourceAccessException e) {
                if (attempt >= maxRetries) {
                    throw new AiTimeoutException("AI service call timed out after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(500); // Backoff before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiServiceException("Retry backoff interrupted", ie);
                }
            } catch (AiProviderException e) {
                throw e;
            } catch (Exception e) {
                throw new AiServiceException("Failed to call AI service", e);
            }
        }
    }
}
