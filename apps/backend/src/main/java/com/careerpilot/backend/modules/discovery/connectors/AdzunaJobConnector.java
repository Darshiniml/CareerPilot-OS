package com.careerpilot.backend.modules.discovery.connectors;

import com.careerpilot.connector.sdk.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Adzuna job search API (https://developer.adzuna.com). Requires ADZUNA_APP_ID / ADZUNA_APP_KEY.
 * Adzuna returns description snippets; salaries flagged as predicted by Adzuna are not stored.
 */
@Component
@Slf4j
public class AdzunaJobConnector implements Connector {

    @Value("${adzuna.app.id:}")
    private String appId;

    @Value("${adzuna.app.key:}")
    private String appKey;

    @Value("${adzuna.country:in}")
    private String country;

    @Value("${careerpilot.discovery.default-keywords:software engineer}")
    private String defaultKeywords;

    private boolean enabled = true;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getConnectorId() {
        return "adzuna";
    }

    @Override
    public String getConnectorType() {
        return "AGGREGATOR";
    }

    @Override
    public String getDisplayName() {
        return "Adzuna Jobs";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private boolean configured() {
        return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
    }

    @Override
    public ConnectorHealth healthCheck() {
        if (!enabled) {
            return ConnectorHealth.builder().connectorId(getConnectorId()).status(ConnectorHealthStatus.DISABLED)
                    .message("Connector disabled").build();
        }
        if (!configured()) {
            return ConnectorHealth.builder().connectorId(getConnectorId()).status(ConnectorHealthStatus.DISABLED)
                    .message("NOT_CONFIGURED: set ADZUNA_APP_ID and ADZUNA_APP_KEY").build();
        }
        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(url(defaultKeywords, null, 1)))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = r.statusCode() == 200;
            return ConnectorHealth.builder().connectorId(getConnectorId())
                    .status(ok ? ConnectorHealthStatus.HEALTHY : ConnectorHealthStatus.UNHEALTHY)
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .lastSuccess(ok ? Instant.now() : null)
                    .message(ok ? "Adzuna API reachable" : "Adzuna returned HTTP " + r.statusCode())
                    .build();
        } catch (Exception e) {
            return ConnectorHealth.builder().connectorId(getConnectorId()).status(ConnectorHealthStatus.UNHEALTHY)
                    .message("Adzuna unreachable: " + e.getClass().getSimpleName()).build();
        }
    }

    private String url(String keywords, String location, int resultsPerPage) {
        StringBuilder sb = new StringBuilder("https://api.adzuna.com/v1/api/jobs/")
                .append(URLEncoder.encode(country, StandardCharsets.UTF_8)).append("/search/1?app_id=")
                .append(URLEncoder.encode(appId, StandardCharsets.UTF_8)).append("&app_key=")
                .append(URLEncoder.encode(appKey, StandardCharsets.UTF_8)).append("&results_per_page=").append(resultsPerPage)
                .append("&content-type=application/json&what=").append(URLEncoder.encode(keywords, StandardCharsets.UTF_8));
        if (location != null && !location.isBlank()) {
            sb.append("&where=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled || !configured()) {
            log.info("[JOB-DISCOVERY] connector=adzuna status=NOT_CONFIGURED");
            return Collections.emptyList();
        }
        Map<String, Object> params = context != null && context.getParameters() != null ? context.getParameters() : Map.of();
        String keywords = Objects.toString(params.getOrDefault("keywords", defaultKeywords), defaultKeywords);
        String location = params.get("location") != null ? params.get("location").toString() : null;
        List<DiscoveredJob> jobs = new ArrayList<>();
        try {
            HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(url(keywords, location, 50)))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                log.warn("[JOB-DISCOVERY] connector=adzuna status=FAILED http={}", r.statusCode());
                return jobs;
            }
            for (JsonNode node : mapper.readTree(r.body()).path("results")) {
                DiscoveredJob job = parse(node);
                if (job != null) jobs.add(job);
            }
        } catch (Exception e) {
            log.warn("[JOB-DISCOVERY] connector=adzuna status=FAILED reason={}", e.getMessage());
        }
        return jobs;
    }

    private DiscoveredJob parse(JsonNode node) {
        String id = ConnectorParsing.text(node, "id");
        String title = ConnectorParsing.plainText(ConnectorParsing.text(node, "title"));
        String company = node.has("company") ? ConnectorParsing.text(node.get("company"), "display_name") : null;
        String description = ConnectorParsing.plainText(ConnectorParsing.text(node, "description"));
        if (id == null || title == null || company == null || description == null) {
            return null;
        }
        String location = node.has("location") ? ConnectorParsing.text(node.get("location"), "display_name") : null;
        String salary = null;
        boolean predicted = "1".equals(ConnectorParsing.text(node, "salary_is_predicted"));
        if (!predicted && node.hasNonNull("salary_min")) {
            salary = node.get("salary_min").asText() + (node.hasNonNull("salary_max") ? " - " + node.get("salary_max").asText() : "");
        }
        return DiscoveredJob.builder()
                .externalId("adzuna-" + id)
                .connectorId(getConnectorId())
                .source("Adzuna")
                .sourceUrl(ConnectorParsing.text(node, "redirect_url"))
                .title(title)
                .company(company)
                .location(location)
                .employmentType(ConnectorParsing.employmentType(ConnectorParsing.text(node, "contract_time")))
                .workMode(ConnectorParsing.workMode(null, location))
                .salary(salary)
                .rawContent(description)
                .postedDate(ConnectorParsing.isoDateTime(ConnectorParsing.text(node, "created")))
                .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                .normalizedTitle(title)
                .normalizedCompany(company)
                .skills(List.of())
                .metadata(Map.of("externalId", id, "contentType", "snippet"))
                .build();
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        return rawJob instanceof DiscoveredJob job ? job : null;
    }
}
