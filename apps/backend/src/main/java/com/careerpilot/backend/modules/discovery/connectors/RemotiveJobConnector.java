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
public class RemotiveJobConnector implements Connector {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean enabled = true;

    public RemotiveJobConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getConnectorId() {
        return "remotive";
    }

    @Override
    public String getConnectorType() {
        return "AGGREGATOR";
    }

    @Override
    public String getDisplayName() {
        return "Remotive Remote Jobs";
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
                    .message("Connector disabled")
                    .build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://remotive.com/api/remote-jobs?limit=1"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return ConnectorHealth.builder()
                        .connectorId(getConnectorId())
                        .status(ConnectorHealthStatus.HEALTHY)
                        .lastSuccess(Instant.now())
                        .message("Remotive public API operational")
                        .build();
            } else {
                return ConnectorHealth.builder()
                        .connectorId(getConnectorId())
                        .status(ConnectorHealthStatus.DEGRADED)
                        .message("Remotive returned HTTP " + response.statusCode())
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

        log.info("[JOB-DISCOVERY] connector=remotive action=discoverJobs_start");
        List<DiscoveredJob> discovered = new ArrayList<>();

        try {
            String url = "https://remotive.com/api/remote-jobs?category=software-dev&limit=20";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode jobsNode = root.get("jobs");
                if (jobsNode != null && jobsNode.isArray()) {
                    for (JsonNode node : jobsNode) {
                        DiscoveredJob job = parseRemotiveJob(node);
                        if (job != null) {
                            discovered.add(job);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[JOB-DISCOVERY] connector=remotive status=FAILED reason={}", e.getMessage(), e);
        }

        log.info("[JOB-DISCOVERY] connector=remotive found={}", discovered.size());
        return discovered;
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) return job;
        return null;
    }

    private DiscoveredJob parseRemotiveJob(JsonNode node) {
        try {
            String idStr = node.has("id") ? node.get("id").asText() : UUID.randomUUID().toString();
            String externalId = "remotive-" + idStr;
            String title = node.has("title") ? node.get("title").asText() : "Remote Software Engineer";
            String company = node.has("company_name") ? node.get("company_name").asText() : "Tech Enterprise";
            String applyUrl = node.has("url") ? node.get("url").asText() : "https://remotive.com";
            String location = node.has("candidate_required_location") ? node.get("candidate_required_location").asText() : "Worldwide Remote";
            String description = node.has("description") ? node.get("description").asText() : title + " at " + company;

            String cleanContent = description.replaceAll("<[^>]*>", " ");

            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode t : node.get("tags")) {
                    tags.add(t.asText());
                }
            }
            if (tags.isEmpty()) {
                tags.addAll(List.of("Java", "Backend", "React", "Python"));
            }

            return DiscoveredJob.builder()
                    .externalId(externalId)
                    .connectorId(getConnectorId())
                    .source("Remotive Remote Jobs")
                    .sourceUrl(applyUrl)
                    .title(title)
                    .company(company)
                    .location(location)
                    .employmentType("FULL_TIME")
                    .workMode("REMOTE")
                    .rawContent(cleanContent.length() > 3000 ? cleanContent.substring(0, 3000) : cleanContent)
                    .postedDate(LocalDateTime.now(ZoneOffset.UTC))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(company.trim())
                    .skills(tags)
                    .metadata(Map.of("externalId", idStr, "category", "software-dev"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
