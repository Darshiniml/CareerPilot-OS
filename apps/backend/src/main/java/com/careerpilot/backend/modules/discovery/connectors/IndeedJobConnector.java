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
public class IndeedJobConnector implements Connector {

    @Value("${indeed.publisher.id:}")
    private String publisherId;

    private boolean enabled = false; // unsupported source

    @Override
    public String getConnectorId() {
        return "indeed";
    }

    @Override
    public String getConnectorType() {
        return "AGGREGATOR";
    }

    @Override
    public String getDisplayName() {
        return "Indeed Jobs";
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
        if (!enabled || publisherId == null || publisherId.isBlank()) {
            log.info("[JOB-DISCOVERY] connector=indeed status=NOT_CONFIGURED reason=Missing Publisher ID");
            return Collections.emptyList();
        }
        log.info("[JOB-DISCOVERY] connector=indeed action=discoverJobs_start");
        return Collections.emptyList();
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob job) return job;
        return null;
    }
}
