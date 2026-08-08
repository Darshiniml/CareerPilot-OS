package com.careerpilot.backend.modules.discovery.connectors;

import com.careerpilot.connector.sdk.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
@Slf4j
public class LeverJobConnector implements Connector {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean enabled = true;

    private static final List<String> DEFAULT_COMPANIES = List.of(
            "netflix", "palantir", "uber", "datadog", "shopify"
    );

    public LeverJobConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getConnectorId() {
        return "lever";
    }

    @Override
    public String getConnectorType() {
        return "DIRECT_ATS";
    }

    @Override
    public String getDisplayName() {
        return "Lever ATS";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public ConnectorHealth healthCheck() {
        if (!enabled) {
            return ConnectorHealth.builder()
                    .connectorId(getConnectorId())
                    .status(ConnectorHealthStatus.DISABLED)
                    .message("Connector disabled by configuration")
                    .build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.lever.co/v0/postings/netflix?mode=json"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return ConnectorHealth.builder()
                        .connectorId(getConnectorId())
                        .status(ConnectorHealthStatus.HEALTHY)
                        .lastSuccess(Instant.now())
                        .message("Lever public API operational")
                        .build();
            } else {
                return ConnectorHealth.builder()
                        .connectorId(getConnectorId())
                        .status(ConnectorHealthStatus.DEGRADED)
                        .message("Lever API returned HTTP " + response.statusCode())
                        .build();
            }
        } catch (Exception e) {
            return ConnectorHealth.builder()
                    .connectorId(getConnectorId())
                    .status(ConnectorHealthStatus.UNHEALTHY)
                    .lastFailure(Instant.now())
                    .message("Connectivity error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled) return Collections.emptyList();

        log.info("[JOB-DISCOVERY] connector=lever action=discoverJobs_start");
        List<DiscoveredJob> discovered = new ArrayList<>();

        for (String company : DEFAULT_COMPANIES) {
            try {
                String url = "https://api.lever.co/v0/postings/" + company + "?mode=json";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode arrayNode = objectMapper.readTree(response.body());
                    if (arrayNode.isArray()) {
                        for (JsonNode node : arrayNode) {
                            DiscoveredJob job = parseLeverJob(company, node);
                            if (job != null) {
                                discovered.add(job);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[JOB-DISCOVERY] connector=lever company={} status=FAILED reason={}", company, e.getMessage());
            }
        }

        log.info("[JOB-DISCOVERY] connector=lever found={}", discovered.size());
        return discovered;
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) {
            return job;
        }
        return null;
    }

    private DiscoveredJob parseLeverJob(String company, JsonNode node) {
        try {
            String externalId = "lever-" + company + "-" + node.get("id").asText();
            String title = node.has("text") ? node.get("text").asText() : "Software Engineer";
            String applyUrl = node.has("hostedUrl") ? node.get("hostedUrl").asText() : "https://jobs.lever.co/" + company;
            
            String location = "Remote";
            if (node.has("categories") && node.get("categories").has("location")) {
                location = node.get("categories").get("location").asText();
            }

            String companyName = company.substring(0, 1).toUpperCase() + company.substring(1);
            String description = title + " position at " + companyName + " in " + location;
            if (node.has("descriptionPlain")) {
                description = node.get("descriptionPlain").asText();
            }

            return DiscoveredJob.builder()
                    .externalId(externalId)
                    .connectorId(getConnectorId())
                    .source("Lever (" + companyName + ")")
                    .sourceUrl(applyUrl)
                    .title(title)
                    .company(companyName)
                    .location(location)
                    .employmentType("FULL_TIME")
                    .workMode(location.toLowerCase().contains("remote") ? "REMOTE" : "HYBRID")
                    .rawContent(description.length() > 3000 ? description.substring(0, 3000) : description)
                    .postedDate(LocalDateTime.now(ZoneOffset.UTC))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(companyName.trim())
                    .skills(List.of("Software Engineering", "Fullstack", "Backend"))
                    .metadata(Map.of("company", company, "externalId", node.get("id").asText()))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
