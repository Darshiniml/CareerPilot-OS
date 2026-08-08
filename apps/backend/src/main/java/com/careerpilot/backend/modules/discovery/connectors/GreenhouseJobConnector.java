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
            String externalId = "greenhouse-" + board + "-" + node.get("id").asText();
            String title = node.has("title") ? node.get("title").asText() : "Software Engineer";
            String applyUrl = node.has("absolute_url") ? node.get("absolute_url").asText() : "https://boards.greenhouse.io/" + board;
            String location = node.has("location") && node.get("location").has("name") ?
                    node.get("location").get("name").asText() : "Remote";
            String content = node.has("content") ? node.get("content").asText() : title + " at " + board;

            // Strip HTML tags for clean text content
            String cleanContent = content.replaceAll("<[^>]*>", " ");

            String companyName = board.substring(0, 1).toUpperCase() + board.substring(1);

            return DiscoveredJob.builder()
                    .externalId(externalId)
                    .connectorId(getConnectorId())
                    .source("Greenhouse (" + companyName + ")")
                    .sourceUrl(applyUrl)
                    .title(title)
                    .company(companyName)
                    .location(location)
                    .employmentType("FULL_TIME")
                    .workMode(location.toLowerCase().contains("remote") ? "REMOTE" : "HYBRID")
                    .rawContent(cleanContent.length() > 3000 ? cleanContent.substring(0, 3000) : cleanContent)
                    .postedDate(LocalDateTime.now(ZoneOffset.UTC))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(companyName.trim())
                    .skills(List.of("Java", "Software Engineering", "Backend"))
                    .metadata(Map.of("board", board, "externalId", node.get("id").asText()))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
