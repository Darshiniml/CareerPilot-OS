package com.careerpilot.backend.modules.discovery.connectors;

import com.careerpilot.connector.sdk.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
@Slf4j
public class WeWorkRemotelyJobConnector implements Connector {

    private final HttpClient httpClient;
    private boolean enabled = true;

    public WeWorkRemotelyJobConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getConnectorId() {
        return "weworkremotely";
    }

    @Override
    public String getConnectorType() {
        return "AGGREGATOR";
    }

    @Override
    public String getDisplayName() {
        return "We Work Remotely";
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
        return ConnectorHealth.builder()
                .connectorId(getConnectorId())
                .status(ConnectorHealthStatus.HEALTHY)
                .lastSuccess(Instant.now())
                .message("WeWorkRemotely RSS feed operational")
                .build();
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled) return Collections.emptyList();

        log.info("[JOB-DISCOVERY] connector=weworkremotely action=discoverJobs_start");
        List<DiscoveredJob> discovered = new ArrayList<>();

        try {
            String url = "https://weworkremotely.com/categories/remote-back-end-programming-jobs.rss";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "CareerPilotOS/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)));

                NodeList items = doc.getElementsByTagName("item");
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    DiscoveredJob job = parseRssItem(item);
                    if (job != null) {
                        discovered.add(job);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[JOB-DISCOVERY] connector=weworkremotely status=FAILED reason={}", e.getMessage(), e);
        }

        log.info("[JOB-DISCOVERY] connector=weworkremotely found={}", discovered.size());
        return discovered;
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) return job;
        return null;
    }

    private DiscoveredJob parseRssItem(Element item) {
        try {
            String title = getTagValue(item, "title");
            String link = getTagValue(item, "link");
            String description = ConnectorParsing.plainText(getTagValue(item, "description"));
            String guid = getTagValue(item, "guid");
            String identity = guid != null && !guid.isBlank() ? guid : link;
            if (title == null || identity == null || description == null) {
                return null; // no stable identity or content: skip, never invent an id
            }
            String company = null;
            String jobTitle = title.trim();
            if (title.contains(" is hiring a ")) {
                String[] parts = title.split(" is hiring a ", 2);
                company = parts[0].trim();
                jobTitle = parts[1].trim();
            } else if (title.contains(":")) {
                String[] parts = title.split(":", 2);
                company = parts[0].trim();
                jobTitle = parts[1].trim();
            }
            if (company == null || company.isBlank()) {
                return null; // company unknown: skipped
            }
            String region = getTagValue(item, "region");
            return DiscoveredJob.builder()
                    .externalId("wwr-" + ConnectorParsing.stableId(identity))
                    .connectorId(getConnectorId())
                    .source("We Work Remotely")
                    .sourceUrl(link)
                    .title(jobTitle)
                    .company(company)
                    .location(region)
                    .employmentType(ConnectorParsing.employmentType(getTagValue(item, "type")))
                    .workMode("REMOTE") // We Work Remotely lists remote jobs only
                    .rawContent(description)
                    .postedDate(ConnectorParsing.rfc1123(getTagValue(item, "pubDate")))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(jobTitle)
                    .normalizedCompany(company)
                    .skills(List.of())
                    .metadata(Map.of("guid", identity))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private String getTagValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
    }
}
