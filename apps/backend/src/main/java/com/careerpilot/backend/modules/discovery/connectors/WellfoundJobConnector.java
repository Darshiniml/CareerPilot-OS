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

    private boolean enabled = false; // unsupported source

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
        // There is no official public job-search API for this source; it is kept registered but unsupported.
        return ConnectorHealth.builder()
                .connectorId(getConnectorId())
                .status(ConnectorHealthStatus.DISABLED)
                .message("UNSUPPORTED: no official public job-search API is available for this source")
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
