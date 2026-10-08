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

    /** Drop every cached match for one candidate (their resume or preferences changed). */
    public void evictCandidate(UUID candidateId) {
        if (candidateId != null) {
            String prefix = candidateId + ":";
            cache.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** Drop every cached match for one job (its requirements were (re)analysed). */
    public void evictJob(UUID jobId) {
        if (jobId != null) {
            String suffix = ":" + jobId;
            cache.keySet().removeIf(k -> k.endsWith(suffix));
        }
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
