package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ApplicationSubmissionRegistry {

    private final Map<String, ApplicationSubmissionConnector> registry = new ConcurrentHashMap<>();
    private final ManualApplicationSubmissionConnector manualFallback;

    public ApplicationSubmissionRegistry(List<ApplicationSubmissionConnector> connectors,
                                         ManualApplicationSubmissionConnector manualFallback) {
        this.manualFallback = manualFallback;
        if (connectors != null) {
            connectors.forEach(c -> {
                registry.put(c.getConnectorId().toLowerCase(), c);
                log.info("[SUBMISSION-REGISTRY] Registered submission connector: {} (mode={})",
                        c.getConnectorId(), c.getSubmissionMode());
            });
        }
    }

    public ApplicationSubmissionConnector getConnector(String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            return manualFallback;
        }
        return Optional.ofNullable(registry.get(connectorId.toLowerCase())).orElse(manualFallback);
    }

    public ApplicationSubmissionMode getSubmissionMode(String connectorId) {
        return getConnector(connectorId).getSubmissionMode();
    }

    public List<ApplicationSubmissionConnector> listConnectors() {
        return List.copyOf(registry.values());
    }
}
