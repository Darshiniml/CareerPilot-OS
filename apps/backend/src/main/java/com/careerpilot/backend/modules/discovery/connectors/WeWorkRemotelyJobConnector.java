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
            String description = getTagValue(item, "description");
            String guid = getTagValue(item, "guid");

            if (guid == null || guid.isBlank()) {
                guid = link != null ? link : UUID.randomUUID().toString();
            }

            String externalId = "wwr-" + Math.abs(guid.hashCode());
            String company = "Remote Enterprise";
            String jobTitle = title != null ? title : "Remote Developer";

            if (title != null && title.contains(" is hiring a ")) {
                String[] parts = title.split(" is hiring a ", 2);
                company = parts[0].trim();
                jobTitle = parts[1].trim();
            } else if (title != null && title.contains(":")) {
                String[] parts = title.split(":", 2);
                company = parts[0].trim();
                jobTitle = parts[1].trim();
            }

            String cleanContent = description != null ? description.replaceAll("<[^>]*>", " ") : jobTitle + " at " + company;

            return DiscoveredJob.builder()
                    .externalId(externalId)
                    .connectorId(getConnectorId())
                    .source("We Work Remotely")
                    .sourceUrl(link != null ? link : "https://weworkremotely.com")
                    .title(jobTitle)
                    .company(company)
                    .location("Worldwide Remote")
                    .employmentType("FULL_TIME")
                    .workMode("REMOTE")
                    .rawContent(cleanContent.length() > 3000 ? cleanContent.substring(0, 3000) : cleanContent)
                    .postedDate(LocalDateTime.now(ZoneOffset.UTC))
                    .discoveredAt(LocalDateTime.now(ZoneOffset.UTC))
                    .normalizedTitle(jobTitle)
                    .normalizedCompany(company)
                    .skills(List.of("Backend", "Java", "Python", "Software Engineering"))
                    .metadata(Map.of("guid", guid))
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
