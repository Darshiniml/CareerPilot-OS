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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Jooble REST API (https://jooble.org/api/about). Requires JOOBLE_API_KEY. Returns description snippets. */
@Component
@Slf4j
public class JoobleJobConnector implements Connector {

    @Value("${jooble.api.key:}")
    private String apiKey;

    @Value("${careerpilot.discovery.default-keywords:software engineer}")
    private String defaultKeywords;

    private boolean enabled = true;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getConnectorId() {
        return "jooble";
    }

    @Override
    public String getConnectorType() {
        return "AGGREGATOR";
    }

    @Override
    public String getDisplayName() {
        return "Jooble";
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
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ConnectorHealth healthCheck() {
        if (!enabled) {
            return ConnectorHealth.builder().connectorId(getConnectorId()).status(ConnectorHealthStatus.DISABLED)
                    .message("Connector disabled").build();
        }
        if (!configured()) {
            return ConnectorHealth.builder().connectorId(getConnectorId()).status(ConnectorHealthStatus.DISABLED)
                    .message("NOT_CONFIGURED: set JOOBLE_API_KEY").build();
        }
        // Jooble has no free health endpoint; configuration is reported, reachability is shown by sync results.
        return ConnectorHealth.builder().connectorId(getConnectorId()).status(ConnectorHealthStatus.UNKNOWN)
                .message("Configured; status is known after the next synchronisation").build();
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled || !configured()) {
            log.info("[JOB-DISCOVERY] connector=jooble status=NOT_CONFIGURED");
            return Collections.emptyList();
        }
        Map<String, Object> params = context != null && context.getParameters() != null ? context.getParameters() : Map.of();
        Map<String, Object> body = new HashMap<>();
        body.put("keywords", Objects.toString(params.getOrDefault("keywords", defaultKeywords), defaultKeywords));
        if (params.get("location") != null) {
            body.put("location", params.get("location").toString());
        }
        List<DiscoveredJob> jobs = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://jooble.org/api/" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> r = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                log.warn("[JOB-DISCOVERY] connector=jooble status=FAILED http={}", r.statusCode());
                return jobs;
            }
            for (JsonNode node : mapper.readTree(r.body()).path("jobs")) {
                DiscoveredJob job = parse(node);
                if (job != null) jobs.add(job);
            }
        } catch (Exception e) {
            log.warn("[JOB-DISCOVERY] connector=jooble status=FAILED reason={}", e.getMessage());
        }
        return jobs;
    }

    private DiscoveredJob parse(JsonNode node) {
        String id = ConnectorParsing.text(node, "id");
        String title = ConnectorParsing.plainText(ConnectorParsing.text(node, "title"));
        String company = ConnectorParsing.text(node, "company");
        String snippet = ConnectorParsing.plainText(ConnectorParsing.text(node, "snippet"));
        String link = ConnectorParsing.text(node, "link");
        if (title == null || company == null || snippet == null || (id == null && link == null)) {
            return null;
        }
        String location = ConnectorParsing.text(node, "location");
        return DiscoveredJob.builder()
                .externalId("jooble-" + (id != null ? id : ConnectorParsing.stableId(link)))
                .connectorId(getConnectorId())
                .source("Jooble" + (ConnectorParsing.text(node, "source") != null ? " (" + ConnectorParsing.text(node, "source") + ")" : ""))
                .sourceUrl(link)
                .title(title)
                .company(company)
                .location(location)
                .employmentType(ConnectorParsing.employmentType(ConnectorParsing.text(node, "type")))
                .workMode(ConnectorParsing.workMode(null, location))
                .salary(ConnectorParsing.text(node, "salary"))
                .rawContent(snippet)
                .postedDate(ConnectorParsing.isoDateTime(ConnectorParsing.text(node, "updated")))
                .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                .normalizedTitle(title)
                .normalizedCompany(company)
                .skills(List.of())
                .metadata(Map.of("contentType", "snippet"))
                .build();
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        return rawJob instanceof DiscoveredJob job ? job : null;
    }
}
