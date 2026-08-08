package com.careerpilot.backend.modules.analytics.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AnalyticsCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, String> localFallbackCache = new ConcurrentHashMap<>();

    public AnalyticsCacheService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, Class<T> clazz) {
        try {
            if (redisTemplate != null) {
                String val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    return objectMapper.readValue(val, clazz);
                }
            }
        } catch (Exception e) {
            log.warn("Redis lookup failed, falling back to local cache or recalculation: {}", e.getMessage());
        }

        String localVal = localFallbackCache.get(key);
        if (localVal != null) {
            try {
                return objectMapper.readValue(localVal, clazz);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void put(String key, Object value, long ttlSeconds) {
        try {
            String jsonVal = objectMapper.writeValueAsString(value);
            localFallbackCache.put(key, jsonVal);

            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, jsonVal, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to write to Redis cache: {}", e.getMessage());
        }
    }

    public void invalidate(String key) {
        localFallbackCache.remove(key);
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("Failed to delete from Redis cache: {}", e.getMessage());
        }
    }

    public void invalidatePattern(String keyPattern) {
        localFallbackCache.keySet().removeIf(k -> k.contains(keyPattern));
        try {
            if (redisTemplate != null) {
                var keys = redisTemplate.keys("*" + keyPattern + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete pattern from Redis cache: {}", e.getMessage());
        }
    }
}
