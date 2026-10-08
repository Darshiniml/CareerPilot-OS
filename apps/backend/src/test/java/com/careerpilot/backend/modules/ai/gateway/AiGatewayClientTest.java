package com.careerpilot.backend.modules.ai.gateway;

import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiTimeoutException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real HTTP client against a local server (no mocks of the transport). */
class AiGatewayClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> scenario = new AtomicReference<>("ok");

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/execute", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] ok = "{\"taskId\":\"00000000-0000-0000-0000-000000000001\",\"status\":\"COMPLETED\",\"result\":{\"answer\":42}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            switch (scenario.get()) {
                case "stall-body" -> {
                    // headers arrive, the body never does: the client must time out reading it
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, ok.length);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                case "provider-unavailable" -> {
                    byte[] err = "{\"error\":{\"code\":\"AI_PROVIDER_UNAVAILABLE\",\"message\":\"ollama is unreachable\"}}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(503, err.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(err);
                    }
                }
                default -> {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, ok.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(ok);
                    }
                }
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsServiceTokenAndReturnsResult() {
        AiGatewayClient client = new AiGatewayClient(baseUrl, 2000, "service-token");
        assertThat(client.run("ANY", Map.of())).containsEntry("answer", 42);
        assertThat(authorization.get()).isEqualTo("Bearer service-token");
    }

    @Test
    void refusesToCallWithoutServiceToken() {
        AiGatewayClient client = new AiGatewayClient(baseUrl, 2000, "");
        assertThatThrownBy(() -> client.run("ANY", Map.of()))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> assertThat(((AiServiceException) e).getCode()).isEqualTo("AI_SERVICE_TOKEN_NOT_CONFIGURED"));
        assertThat(calls.get()).isZero();
    }

    @Test
    void timeoutWhileReadingBodyIsAnExplicitTimeoutAndNotRetried() {
        scenario.set("stall-body");
        AiGatewayClient client = new AiGatewayClient(baseUrl, 500, "service-token");
        assertThatThrownBy(() -> client.run("ANY", Map.of()))
                .isInstanceOf(AiTimeoutException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void providerErrorKeepsTheAiServiceErrorCode() {
        scenario.set("provider-unavailable");
        AiGatewayClient client = new AiGatewayClient(baseUrl, 2000, "service-token");
        assertThatThrownBy(() -> client.run("ANY", Map.of()))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> {
                    AiServiceException ex = (AiServiceException) e;
                    assertThat(ex.getCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
                    assertThat(ex.getHttpStatus()).isEqualTo(503);
                });
    }
}
