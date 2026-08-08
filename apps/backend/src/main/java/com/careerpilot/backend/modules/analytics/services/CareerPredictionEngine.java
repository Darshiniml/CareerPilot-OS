package com.careerpilot.backend.modules.analytics.services;

import java.util.Map;
import java.util.UUID;

public interface CareerPredictionEngine {
    Map<String, Object> predictImprovements(UUID candidateId);
}
