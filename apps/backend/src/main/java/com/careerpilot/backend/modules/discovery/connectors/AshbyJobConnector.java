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
public class AshbyJobConnector implements Connector {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean enabled = true;

    private static final List<String> DEFAULT_COMPANIES = List.of(
            "ashby", "linear", "vercel", "ramp", "replit"
    );

    public AshbyJobConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getConnectorId() {
        return "ashby";
    }

    @Override
    public String getConnectorType() {
        return "DIRECT_ATS";
    }

    @Override
    public String getDisplayName() {
        return "Ashby ATS";
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
        return ConnectorHealth.builder()
                .connectorId(getConnectorId())
                .status(ConnectorHealthStatus.HEALTHY)
                .lastSuccess(Instant.now())
                .message("Ashby ATS integration active")
                .build();
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled) return Collections.emptyList();

        log.info("[JOB-DISCOVERY] connector=ashby action=discoverJobs_start");
        List<DiscoveredJob> discovered = new ArrayList<>();

        for (String company : DEFAULT_COMPANIES) {
            try {
                String url = "https://api.ashbyhq.com/posting-api/job-board/" + company;
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
                            DiscoveredJob job = parseAshbyJob(company, node);
                            if (job != null) {
                                discovered.add(job);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[JOB-DISCOVERY] connector=ashby company={} status=FAILED reason={}", company, e.getMessage());
            }
        }

        log.info("[JOB-DISCOVERY] connector=ashby found={}", discovered.size());
        return discovered;
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) {
            return job;
        }
        return null;
    }

    private DiscoveredJob parseAshbyJob(String company, JsonNode node) {
        try {
            String id = ConnectorParsing.text(node, "id");
            String title = ConnectorParsing.text(node, "title");
            String description = ConnectorParsing.text(node, "descriptionPlain");
            if (description == null) {
                description = ConnectorParsing.plainText(ConnectorParsing.text(node, "descriptionHtml"));
            }
            if (id == null || title == null || description == null) {
                return null; // Ashby returns descriptions only with includeCompensation/description; skip otherwise
            }
            String location = ConnectorParsing.text(node, "location");
            if (location == null) location = ConnectorParsing.text(node, "locationName");
            String workMode = node.hasNonNull("isRemote") && node.get("isRemote").asBoolean() ? "REMOTE"
                    : ConnectorParsing.workMode(ConnectorParsing.text(node, "workplaceType"), location);
            String companyName = ConnectorParsing.capitalize(company);
            return DiscoveredJob.builder()
                    .externalId("ashby-" + company + "-" + id)
                    .connectorId(getConnectorId())
                    .source("Ashby (" + companyName + ")")
                    .sourceUrl(ConnectorParsing.text(node, "jobUrl"))
                    .title(title)
                    .company(companyName)
                    .location(location)
                    .employmentType(ConnectorParsing.employmentType(ConnectorParsing.text(node, "employmentType")))
                    .workMode(workMode)
                    .rawContent(description.length() > ConnectorParsing.MAX_CONTENT ? description.substring(0, ConnectorParsing.MAX_CONTENT) : description)
                    .postedDate(ConnectorParsing.isoDateTime(ConnectorParsing.text(node, "publishedAt")))
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
