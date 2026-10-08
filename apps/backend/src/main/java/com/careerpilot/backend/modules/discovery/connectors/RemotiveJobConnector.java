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
            String id = ConnectorParsing.text(node, "id");
            String title = ConnectorParsing.text(node, "title");
            String company = ConnectorParsing.text(node, "company_name");
            String description = ConnectorParsing.plainText(ConnectorParsing.text(node, "description"));
            if (id == null || title == null || company == null || description == null) {
                return null;
            }
            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode t : node.get("tags")) {
                    tags.add(t.asText());
                }
            }
            return DiscoveredJob.builder()
                    .externalId("remotive-" + id)
                    .connectorId(getConnectorId())
                    .source("Remotive Remote Jobs")
                    .sourceUrl(ConnectorParsing.text(node, "url"))
                    .title(title)
                    .company(company)
                    .location(ConnectorParsing.text(node, "candidate_required_location"))
                    .employmentType(ConnectorParsing.employmentType(ConnectorParsing.text(node, "job_type")))
                    .workMode("REMOTE") // Remotive lists remote jobs only
                    .salary(ConnectorParsing.text(node, "salary"))
                    .rawContent(description)
                    .postedDate(ConnectorParsing.isoDateTime(ConnectorParsing.text(node, "publication_date")))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(company.trim())
                    .skills(tags)
                    .metadata(Map.of("externalId", id, "category", Objects.toString(ConnectorParsing.text(node, "category"), ""), "tags", tags))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
