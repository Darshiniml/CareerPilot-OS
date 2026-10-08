package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionCapability;
import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ApplicationSubmissionRegistry {

    private final Map<String, ApplicationSubmissionConnector> registry = new ConcurrentHashMap<>();
    private final ManualApplicationSubmissionConnector manualFallback;

    private static final List<String> KNOWN_SOURCES = List.of(
            "greenhouse", "lever", "ashby",
            "remotive", "weworkremotely", "adzuna", "jooble", "wellfound", "indeed"
    );

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

    public ApplicationSubmissionCapability getSubmissionCapability(String connectorId) {
        String key = (connectorId != null && !connectorId.isBlank()) ? connectorId.toLowerCase() : "manual-fallback";
        ApplicationSubmissionConnector connector = registry.get(key);

        if (connector != null) {
            return ApplicationSubmissionCapability.builder()
                    .source(connector.getConnectorId())
                    .submissionMode(connector.getSubmissionMode())
                    .enabled(true)
                    .requiresCredentials(connector.getSubmissionMode() == ApplicationSubmissionMode.API_SUPPORTED || connector.getSubmissionMode() == ApplicationSubmissionMode.LICENSED_INTEGRATION)
                    .supportsSubmission(connector.isSubmissionSupported())
                    .supportsVerification(false)
                    .supportsStatusTracking(false)
                    .reason(connector.isSubmissionSupported() ? "Permitted submission API active" : "Discovery source does not expose a permitted application submission API")
                    .configurationStatus("REGISTERED")
                    // Submission endpoints are not probed, so health is not claimed.
                    .healthStatus("NOT_MONITORED")
                    .build();
        }

        // Manual / Aggregator fallback capability
        return ApplicationSubmissionCapability.builder()
                .source(key)
                .submissionMode(ApplicationSubmissionMode.MANUAL_REQUIRED)
                .enabled(true)
                .requiresCredentials(false)
                .supportsSubmission(false)
                .supportsVerification(false)
                .supportsStatusTracking(false)
                .reason("Aggregator provides job discovery; requires candidate manual action via official applyUrl")
                // Manual applications need no submission configuration; nothing is monitored.
                .configurationStatus(KNOWN_SOURCES.contains(key) ? "NOT_REQUIRED" : "NOT_CONFIGURED")
                .healthStatus("NOT_APPLICABLE")
                .build();
    }

    public List<ApplicationSubmissionCapability> getSubmissionCapabilities() {
        List<ApplicationSubmissionCapability> capabilities = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        // Add registered connectors
        for (ApplicationSubmissionConnector c : registry.values()) {
            capabilities.add(getSubmissionCapability(c.getConnectorId()));
            processed.add(c.getConnectorId().toLowerCase());
        }

        // Add known discovery connectors
        for (String source : KNOWN_SOURCES) {
            if (!processed.contains(source)) {
                capabilities.add(getSubmissionCapability(source));
            }
        }

        return capabilities;
    }

    public List<ApplicationSubmissionConnector> listConnectors() {
        return List.copyOf(registry.values());
    }
}
