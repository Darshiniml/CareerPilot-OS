package com.careerpilot.backend.modules.ai.matching;

import com.careerpilot.backend.modules.ai.matching.config.MatchingWeightsConfig;
import com.careerpilot.shared.dto.ai.matching.MatchResultDto;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MatchingCache {

    private final ConcurrentHashMap<String, MatchResultDto> cache = new ConcurrentHashMap<>();
    private final MatchingWeightsConfig weightsConfig;

    public MatchingCache(MatchingWeightsConfig weightsConfig) {
        this.weightsConfig = weightsConfig;
    }

    public static String generateCacheKey(UUID candidateId, UUID jobId) {
        return (candidateId != null ? candidateId.toString() : "anon") + ":" + (jobId != null ? jobId.toString() : "unknown");
    }

    public MatchResultDto get(String key) {
        if (key == null) return null;
        return cache.get(key);
    }

    public void put(String key, MatchResultDto result) {
        if (key != null && result != null) {
            cache.put(key, result);
        }
    }

    public void remove(String key) {
        if (key != null) cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public MatchingWeightsConfig getWeightsConfig() {
        return weightsConfig;
    }
}
