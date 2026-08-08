package com.careerpilot.backend.modules.agent.services;

import java.util.Map;
import java.util.UUID;

public interface ReferenceSourceProvider {
    String getProviderName();
    Map<String, Object> queryPublicSignals(UUID userId, String companyName) throws Exception;
}
