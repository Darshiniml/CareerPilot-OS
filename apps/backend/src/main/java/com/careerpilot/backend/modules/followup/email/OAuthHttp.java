package com.careerpilot.backend.modules.followup.email;

import com.careerpilot.backend.config.CodedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Small HTTP helper shared by the OAuth mail providers. */
final class OAuthHttp {

    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private OAuthHttp() {
    }

    static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    static JsonNode postForm(String url, Map<String, String> params, String provider) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build();
        return send(request, provider);
    }

    static JsonNode postJson(String url, String bearer, Object body, String provider) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            return send(request, provider);
        } catch (IOException e) {
            throw new CodedException("Could not serialise the request", "EMAIL_PROVIDER_ERROR", 502, e);
        }
    }

    static JsonNode get(String url, String bearer, String provider) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + bearer)
                .GET().build();
        return send(request, provider);
    }

    private static JsonNode send(HttpRequest request, String provider) {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new CodedException(provider + " rejected the authorisation (HTTP " + status + "); reconnect the mailbox",
                        "EMAIL_AUTH_FAILED", 502, null);
            }
            if (status >= 400) {
                throw new CodedException(provider + " returned HTTP " + status, "EMAIL_PROVIDER_ERROR", 502, null);
            }
            String body = response.body();
            return body == null || body.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(body);
        } catch (IOException e) {
            throw new CodedException(provider + " is unreachable", "EMAIL_PROVIDER_UNAVAILABLE", 503, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodedException("Interrupted", "EMAIL_PROVIDER_UNAVAILABLE", 503, e);
        }
    }

    /** Read a claim from a JWT received directly from the provider's token endpoint over TLS. */
    static String jwtClaim(String jwt, String claim) {
        try {
            String[] parts = jwt.split("\\.");
            JsonNode payload = MAPPER.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
            return payload.hasNonNull(claim) ? payload.get(claim).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
