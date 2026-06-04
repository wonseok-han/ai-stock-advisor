package com.nowini.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Jackson + StringRedisTemplate 기반 캐시 추상화.
 * Key 컨벤션: profile:{ticker}, quote:{ticker}, search:{query}, ai:{ticker}:v{version}.
 * 참조: docs/02-design/features/mvp.design.md §3.4.
 */
@Component
public class RedisCacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);
    /** 불완전/실패 결과는 이 짧은 TTL로만 캐시 → 곧 자동 재시도(자가복구). 수동 캐시 삭제 불필요. */
    private static final Duration FALLBACK_TTL = Duration.ofSeconds(60);

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

    /**
     * Cache-or-load with 유효성 판정. {@code cacheable}이 true면 정상 TTL, false(불완전/실패)면
     * 짧은 {@link #FALLBACK_TTL}로만 캐시해 곧 자동 재시도되게 한다(자가복구).
     * 일시적 업스트림 실패가 긴 TTL로 고착돼 수동 캐시 삭제가 필요하던 문제를 방지.
     */
    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl,
                           Supplier<T> loader, Predicate<T> cacheable) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            set(key, loaded, cacheable.test(loaded) ? ttl : FALLBACK_TTL);
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
