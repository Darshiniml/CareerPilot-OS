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
            String id = ConnectorParsing.text(node, "id");
            String title = ConnectorParsing.text(node, "text");
            String description = ConnectorParsing.text(node, "descriptionPlain");
            if (id == null || title == null || description == null) {
                return null;
            }
            StringBuilder content = new StringBuilder(description);
            if (node.has("lists") && node.get("lists").isArray()) {
                for (JsonNode list : node.get("lists")) {
                    String heading = ConnectorParsing.text(list, "text");
                    String body = ConnectorParsing.plainText(ConnectorParsing.text(list, "content"));
                    if (heading != null) content.append("\n\n").append(heading);
                    if (body != null) content.append("\n").append(body);
                }
            }
            String additional = ConnectorParsing.text(node, "additionalPlain");
            if (additional != null) content.append("\n\n").append(additional);
            JsonNode categories = node.get("categories");
            String location = categories != null ? ConnectorParsing.text(categories, "location") : null;
            String commitment = categories != null ? ConnectorParsing.text(categories, "commitment") : null;
            String companyName = ConnectorParsing.capitalize(company);
            String text = content.length() > ConnectorParsing.MAX_CONTENT ? content.substring(0, ConnectorParsing.MAX_CONTENT) : content.toString();
            return DiscoveredJob.builder()
                    .externalId("lever-" + company + "-" + id)
                    .connectorId(getConnectorId())
                    .source("Lever (" + companyName + ")")
                    .sourceUrl(ConnectorParsing.text(node, "hostedUrl"))
                    .title(title)
                    .company(companyName)
                    .location(location)
                    .employmentType(ConnectorParsing.employmentType(commitment))
                    .workMode(ConnectorParsing.workMode(ConnectorParsing.text(node, "workplaceType"), location))
                    .rawContent(text)
                    .postedDate(ConnectorParsing.epochMillis(node, "createdAt"))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(companyName)
                    .skills(List.of())
                    .metadata(Map.of("company", company, "externalId", id))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
