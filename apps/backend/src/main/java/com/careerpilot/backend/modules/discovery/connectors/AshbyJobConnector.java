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
            String externalId = "ashby-" + company + "-" + node.get("id").asText();
            String title = node.has("title") ? node.get("title").asText() : "Software Developer";
            String applyUrl = node.has("jobUrl") ? node.get("jobUrl").asText() : "https://jobs.ashbyhq.com/" + company;
            String location = node.has("locationName") ? node.get("locationName").asText() : "Remote";
            String companyName = company.substring(0, 1).toUpperCase() + company.substring(1);

            return DiscoveredJob.builder()
                    .externalId(externalId)
                    .connectorId(getConnectorId())
                    .source("Ashby (" + companyName + ")")
                    .sourceUrl(applyUrl)
                    .title(title)
                    .company(companyName)
                    .location(location)
                    .employmentType("FULL_TIME")
                    .workMode(location.toLowerCase().contains("remote") ? "REMOTE" : "HYBRID")
                    .rawContent(title + " role at " + companyName + " in " + location)
                    .postedDate(LocalDateTime.now(ZoneOffset.UTC))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(title.trim())
                    .normalizedCompany(companyName.trim())
                    .skills(List.of("Software Engineering", "Fullstack", "Frontend"))
                    .metadata(Map.of("company", company, "externalId", node.get("id").asText()))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
