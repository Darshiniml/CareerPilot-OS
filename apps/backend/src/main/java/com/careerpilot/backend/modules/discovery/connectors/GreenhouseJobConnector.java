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
public class GreenhouseJobConnector implements Connector {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean enabled = true;

    // List of known public Greenhouse boards for direct company integration
    private static final List<String> DEFAULT_BOARDS = List.of(
            "canonical", "airbnb", "github", "stripe", "cloudflare",
            "spotify", "discord", "figma", "hashicorp", "datadog"
    );

    public GreenhouseJobConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getConnectorId() {
        return "greenhouse";
    }

    @Override
    public String getConnectorType() {
        return "DIRECT_ATS";
    }

    @Override
    public String getDisplayName() {
        return "Greenhouse ATS";
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
            // Verify connectivity against Canonical public Greenhouse board
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://boards-api.greenhouse.io/v1/boards/canonical/jobs"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return ConnectorHealth.builder()
                        .connectorId(getConnectorId())
                        .status(ConnectorHealthStatus.HEALTHY)
                        .lastSuccess(Instant.now())
                        .message("Greenhouse public API operational")
                        .build();
            } else {
                return ConnectorHealth.builder()
                        .connectorId(getConnectorId())
                        .status(ConnectorHealthStatus.DEGRADED)
                        .message("Greenhouse API returned HTTP " + response.statusCode())
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

        log.info("[JOB-DISCOVERY] connector=greenhouse action=discoverJobs_start");
        List<DiscoveredJob> discovered = new ArrayList<>();

        for (String board : DEFAULT_BOARDS) {
            try {
                String url = "https://boards-api.greenhouse.io/v1/boards/" + board + "/jobs?content=true";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode jobsNode = root.get("jobs");
                    if (jobsNode != null && jobsNode.isArray()) {
                        for (JsonNode node : jobsNode) {
                            DiscoveredJob job = parseGreenhouseJob(board, node);
                            if (job != null) {
                                discovered.add(job);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[JOB-DISCOVERY] connector=greenhouse board={} status=FAILED reason={}", board, e.getMessage());
            }
        }

        log.info("[JOB-DISCOVERY] connector=greenhouse found={}", discovered.size());
        return discovered;
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) {
            return job;
        }
        return null;
    }

    private DiscoveredJob parseGreenhouseJob(String board, JsonNode node) {
        try {
            String id = ConnectorParsing.text(node, "id");
            String title = ConnectorParsing.text(node, "title");
            String content = ConnectorParsing.plainText(ConnectorParsing.text(node, "content"));
            if (id == null || title == null || content == null) {
                return null; // incomplete posting: skipped rather than filled with guesses
            }
            String location = node.has("location") ? ConnectorParsing.text(node.get("location"), "name") : null;
            String companyName = ConnectorParsing.capitalize(board);
            return DiscoveredJob.builder()
                    .externalId("greenhouse-" + board + "-" + id)
                    .connectorId(getConnectorId())
                    .source("Greenhouse (" + companyName + ")")
                    .sourceUrl(ConnectorParsing.text(node, "absolute_url"))
                    .title(title)
                    .company(companyName)
                    .location(location)
                    .employmentType(null) // not provided by the Greenhouse job board API
                    .workMode(ConnectorParsing.workMode(null, location))
                    .rawContent(content)
                    .postedDate(ConnectorParsing.isoDateTime(ConnectorParsing.text(node, "updated_at")))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(companyName)
                    .skills(List.of())
                    .metadata(Map.of("board", board, "externalId", id, "dateField", "updated_at"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
