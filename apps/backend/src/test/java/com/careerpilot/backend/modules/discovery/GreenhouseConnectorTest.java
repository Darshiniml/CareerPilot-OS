package com.careerpilot.backend.modules.discovery;

import com.careerpilot.backend.modules.discovery.connectors.GreenhouseJobConnector;
import com.careerpilot.connector.sdk.ConnectorHealth;
import com.careerpilot.connector.sdk.ConnectorHealthStatus;
import com.careerpilot.connector.sdk.DiscoveryContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreenhouseConnectorTest {

    @Test
    void testGreenhousePropertiesAndHealth() {
        GreenhouseJobConnector connector = new GreenhouseJobConnector();
        assertThat(connector.getConnectorId()).isEqualTo("greenhouse");
        assertThat(connector.getConnectorType()).isEqualTo("DIRECT_ATS");
        assertThat(connector.getDisplayName()).isEqualTo("Greenhouse ATS");
        assertThat(connector.isEnabled()).isTrue();

        ConnectorHealth health = connector.healthCheck();
        assertThat(health.getStatus()).isNotEqualTo(ConnectorHealthStatus.DISABLED);
    }
}
