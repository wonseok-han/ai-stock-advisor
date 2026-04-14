package com.aistockadvisor.ai.service;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * AI 시그널 호출 rate limit (Redis 기반, 분 단위 sliding bucket).
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §7.2
 *
 * <p>구현: {@code INCR ai:rate:{bucket}} + 최초 호출 시 TTL 60s. bucket4j 미도입 — 외부 의존 최소화.
 * Redis 장애 시 fail-open (요청 허용).
 */
@Component
public class AiSignalRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiSignalRateLimiter.class);
    private static final Duration BUCKET_TTL = Duration.ofSeconds(65);

    private final StringRedisTemplate redis;
    private final int limitPerMinute;

    public AiSignalRateLimiter(StringRedisTemplate redis,
                               @Value("${app.ai.signal.rate-limit-rpm:15}") int limitPerMinute) {
        this.redis = redis;
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    public void checkOrThrow() {
        long bucket = Instant.now().getEpochSecond() / 60;
        String key = "ai:rate:" + bucket;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, BUCKET_TTL);
            }
            if (count != null && count > limitPerMinute) {
                log.info("ai-signal rate limit exceeded count={} limit={}", count, limitPerMinute);
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT,
                        "AI 분석 요청이 일시적으로 많습니다. 잠시 후 다시 시도해주세요.", null);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ex) {
            log.warn("ai-signal rate limit redis failure — fail-open reason={}", ex.getMessage());
        }
    }
}
