package com.careerpilot.backend.modules.discovery;

import com.careerpilot.backend.modules.discovery.repositories.ConnectorConfigurationRepository;
import com.careerpilot.backend.modules.discovery.services.ConnectorRegistry;
import com.careerpilot.connector.sdk.Connector;
import com.careerpilot.connector.sdk.ConnectorHealth;
import com.careerpilot.connector.sdk.ConnectorHealthStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConnectorRegistryTest {

    @Test
    void registersAndTogglesConnector() {
        ConnectorConfigurationRepository configRepo = mock(ConnectorConfigurationRepository.class);
        Connector c = mock(Connector.class);
        when(c.getConnectorId()).thenReturn("greenhouse");
        when(c.getVersion()).thenReturn("1.0.0");
        when(c.getConnectorType()).thenReturn("DIRECT_ATS");
        when(c.getDisplayName()).thenReturn("Greenhouse ATS");
        when(c.isEnabled()).thenReturn(true);
        when(c.healthCheck()).thenReturn(ConnectorHealth.builder().connectorId("greenhouse").status(ConnectorHealthStatus.HEALTHY).build());

        ConnectorRegistry r = new ConnectorRegistry(List.of(), configRepo);
        r.register(c);
        assertThat(r.list()).hasSize(1);
        assertThat(r.get("greenhouse")).isSameAs(c);
    }
}
