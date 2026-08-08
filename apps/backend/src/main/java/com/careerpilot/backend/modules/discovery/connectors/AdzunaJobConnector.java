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
public class AdzunaJobConnector implements Connector {

    @Value("${adzuna.app.id:}")
    private String appId;

    @Value("${adzuna.app.key:}")
    private String appKey;

    private boolean enabled = true;

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
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            return ConnectorHealth.builder()
                    .connectorId(getConnectorId())
                    .status(ConnectorHealthStatus.UNHEALTHY)
                    .message("NOT_CONFIGURED: ADZUNA_APP_ID and ADZUNA_APP_KEY environment variables required")
                    .build();
        }
        return ConnectorHealth.builder()
                .connectorId(getConnectorId())
                .status(ConnectorHealthStatus.HEALTHY)
                .lastSuccess(Instant.now())
                .message("Adzuna API configured")
                .build();
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        if (!enabled || appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            log.info("[JOB-DISCOVERY] connector=adzuna status=NOT_CONFIGURED reason=Missing API Credentials");
            return Collections.emptyList();
        }
        log.info("[JOB-DISCOVERY] connector=adzuna action=discoverJobs_start");
        return Collections.emptyList();
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) return job;
        return null;
    }
}
