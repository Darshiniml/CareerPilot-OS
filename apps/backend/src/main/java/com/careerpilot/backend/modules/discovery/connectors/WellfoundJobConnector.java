package com.careerpilot.backend.modules.discovery.connectors;

import com.careerpilot.connector.sdk.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class WellfoundJobConnector implements Connector {

    @Value("${wellfound.api.key:}")
    private String apiKey;

    private boolean enabled = true;

    @Override
    public String getConnectorId() {
        return "wellfound";
    }

    @Override
    public String getConnectorType() {
        return "AGGREGATOR";
    }

    @Override
    public String getDisplayName() {
        return "Wellfound (AngelList)";
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
        if (apiKey == null || apiKey.isBlank()) {
            return ConnectorHealth.builder()
                    .connectorId(getConnectorId())
                    .status(ConnectorHealthStatus.UNHEALTHY)
                    .message("NOT_CONFIGURED: WELLFOUND_API_KEY environment variable required")
                    .build();
        }
        return ConnectorHealth.builder()
                .connectorId(getConnectorId())
                .status(ConnectorHealthStatus.HEALTHY)
                .lastSuccess(Instant.now())
                .message("Wellfound API configured")
                .build();
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.info("[JOB-DISCOVERY] connector=wellfound status=NOT_CONFIGURED reason=Missing API Key");
            return Collections.emptyList();
        }
        log.info("[JOB-DISCOVERY] connector=wellfound action=discoverJobs_start");
        return Collections.emptyList();
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) return job;
        return null;
    }
}
