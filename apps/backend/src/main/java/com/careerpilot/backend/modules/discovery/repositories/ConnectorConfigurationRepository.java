package com.careerpilot.backend.modules.discovery.repositories;

import com.careerpilot.backend.modules.discovery.domain.ConnectorConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorConfigurationRepository extends JpaRepository<ConnectorConfiguration, String> {
}
