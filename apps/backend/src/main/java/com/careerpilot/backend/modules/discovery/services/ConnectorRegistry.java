package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.connector.sdk.*;
import com.careerpilot.backend.modules.discovery.domain.ConnectorConfiguration;
import com.careerpilot.backend.modules.discovery.domain.ConnectorRegistration;
import com.careerpilot.backend.modules.discovery.repositories.ConnectorConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ConnectorRegistry {

    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();
    private final ConnectorConfigurationRepository configRepo;

    public ConnectorRegistry(List<Connector> discovered, ConnectorConfigurationRepository configRepo) {
        this.configRepo = configRepo;
        for (Connector c : discovered) {
            register(c);
        }
    }

    public ConnectorRegistration register(Connector c) {
        Objects.requireNonNull(c);
        if (c.getConnectorId() == null || c.getConnectorId().isBlank()) {
            throw new IllegalArgumentException("Connector id is required");
        }

        // Restore saved configuration state if present
        Optional<ConnectorConfiguration> savedConfig = configRepo.findById(c.getConnectorId());
        if (savedConfig.isPresent()) {
            c.setEnabled(savedConfig.get().isEnabled());
        } else {
            // Default configuration initialization
            saveConfig(c, c.isEnabled(), c.healthCheck().getStatus().name());
        }

        connectors.put(c.getConnectorId(), c);
        return describe(c);
    }

    public Connector get(String id) {
        return Optional.ofNullable(connectors.get(id))
                .orElseThrow(() -> new NoSuchElementException("Connector not found: " + id));
    }

    public List<ConnectorRegistration> list() {
        return connectors.values().stream()
                .map(this::describe)
                .sorted(Comparator.comparing(ConnectorRegistration::getId))
                .toList();
    }

    public List<Connector> enabled() {
        return connectors.values().stream()
                .filter(Connector::isEnabled)
                .toList();
    }

    public ConnectorRegistration enable(String id, boolean enabled) {
        Connector c = get(id);
        c.setEnabled(enabled);
        saveConfig(c, enabled, c.healthCheck().getStatus().name());
        log.info("[JOB-DISCOVERY] connector={} enabled={}", id, enabled);
        return describe(c);
    }

    public List<ConnectorHealth> health() {
        return connectors.values().stream()
                .map(c -> {
                    if (!c.isEnabled()) {
                        return ConnectorHealth.builder()
                                .connectorId(c.getConnectorId())
                                .status(ConnectorHealthStatus.DISABLED)
                                .message("Connector disabled")
                                .build();
                    }
                    ConnectorHealth h = c.healthCheck();
                    saveConfig(c, c.isEnabled(), h.getStatus().name());
                    return h;
                })
                .toList();
    }

    private void saveConfig(Connector c, boolean enabled, String status) {
        try {
            ConnectorConfiguration config = ConnectorConfiguration.builder()
                    .connectorId(c.getConnectorId())
                    .connectorName(c.getDisplayName())
                    .connectorType(c.getConnectorType())
                    .enabled(enabled)
                    .healthStatus(status)
                    .updatedAt(Instant.now())
                    .build();
            configRepo.save(config);
        } catch (Exception e) {
            log.warn("Failed to persist connector configuration for {}: {}", c.getConnectorId(), e.getMessage());
        }
    }

    private ConnectorRegistration describe(Connector c) {
        ConnectorHealth health = c.isEnabled() ? c.healthCheck() : ConnectorHealth.builder().status(ConnectorHealthStatus.DISABLED).build();
        return ConnectorRegistration.builder()
                .id(c.getConnectorId())
                .type(c.getConnectorType())
                .name(c.getDisplayName())
                .version(c.getVersion())
                .enabled(c.isEnabled())
                .build();
    }
}
