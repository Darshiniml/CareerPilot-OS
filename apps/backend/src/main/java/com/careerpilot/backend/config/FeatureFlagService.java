package com.careerpilot.backend.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FeatureFlagService {

    private final Environment environment;

    public FeatureFlagService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Check if a feature is enabled globally.
     */
    public boolean isEnabled(FeatureFlag flag) {
        String propertyKey = "features.flags." + flag.name().toLowerCase().replace("_", "-");
        return environment.getProperty(propertyKey, Boolean.class, false);
    }

    /**
     * Check if a feature is enabled for a specific user (allows beta testing rollout).
     */
    public boolean isEnabled(FeatureFlag flag, UUID userId) {
        // Fallback to global checks for now, but ready for user segmentation
        return isEnabled(flag);
    }
}
