package com.aistockadvisor.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Jackson + StringRedisTemplate 기반 캐시 추상화.
 * Key 컨벤션: profile:{ticker}, quote:{ticker}, search:{query}, ai:{ticker}:v{version}.
 * 참조: docs/02-design/features/mvp.design.md §3.4.
 */
@Component
public class RedisCacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCacheAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Cache-or-load. Redis 장애 시 supplier 결과 그대로 반환 (fail-open). */
    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            set(key, loaded, ttl);
        }
        return loaded;
    }

    public <T> T get(String key, TypeReference<T> type) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null) {
                return null;
            }
            return objectMapper.readValue(raw, type);
        } catch (Exception ex) {
            log.warn("redis get failed key={} reason={}", key, ex.getMessage());
            return null;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.warn("redis set failed key={} reason={}", key, ex.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception ex) {
            log.warn("redis del failed key={} reason={}", key, ex.getMessage());
        }
    }
}
